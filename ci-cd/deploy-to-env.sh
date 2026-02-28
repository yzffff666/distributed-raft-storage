#!/bin/bash

# 自动化部署脚本
# 用法: ./deploy-to-env.sh <environment> <version> [options]

set -euo pipefail

# 脚本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
HELM_CHART_PATH="${PROJECT_ROOT}/helm/raft-storage"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示使用说明
show_usage() {
    cat << EOF
用法: $0 <environment> <version> [options]

参数:
  environment    目标环境 (dev|test|prod)
  version        镜像版本标签

选项:
  --dry-run      只显示将要执行的命令，不实际执行
  --force        强制部署，跳过确认
  --rollback     回滚到上一个版本
  --debug        启用调试模式
  --timeout      部署超时时间 (默认: 600s)
  --help         显示此帮助信息

示例:
  $0 dev latest
  $0 test v1.9.0 --dry-run
  $0 prod v1.9.0 --force
  $0 prod --rollback

EOF
}

# 解析命令行参数
parse_args() {
    if [[ $# -lt 1 ]]; then
        log_error "缺少必需参数"
        show_usage
        exit 1
    fi

    ENVIRONMENT="$1"
    shift

    # 验证环境参数
    case "$ENVIRONMENT" in
        dev|test|prod)
            ;;
        *)
            log_error "无效的环境: $ENVIRONMENT"
            log_error "支持的环境: dev, test, prod"
            exit 1
            ;;
    esac

    # 默认值
    VERSION=""
    DRY_RUN=false
    FORCE=false
    ROLLBACK=false
    DEBUG=false
    TIMEOUT="600s"

    # 解析选项
    while [[ $# -gt 0 ]]; do
        case $1 in
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --force)
                FORCE=true
                shift
                ;;
            --rollback)
                ROLLBACK=true
                shift
                ;;
            --debug)
                DEBUG=true
                set -x
                shift
                ;;
            --timeout)
                TIMEOUT="$2"
                shift 2
                ;;
            --help)
                show_usage
                exit 0
                ;;
            -*)
                log_error "未知选项: $1"
                show_usage
                exit 1
                ;;
            *)
                if [[ -z "$VERSION" ]]; then
                    VERSION="$1"
                else
                    log_error "太多参数: $1"
                    show_usage
                    exit 1
                fi
                shift
                ;;
        esac
    done

    # 验证版本参数（回滚时不需要）
    if [[ "$ROLLBACK" == false && -z "$VERSION" ]]; then
        log_error "缺少版本参数"
        show_usage
        exit 1
    fi
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."
    
    local missing_deps=()
    
    # 检查必需的命令
    for cmd in kubectl helm docker; do
        if ! command -v "$cmd" &> /dev/null; then
            missing_deps+=("$cmd")
        fi
    done
    
    if [[ ${#missing_deps[@]} -gt 0 ]]; then
        log_error "缺少依赖: ${missing_deps[*]}"
        exit 1
    fi
    
    # 检查Kubernetes连接
    if ! kubectl cluster-info &> /dev/null; then
        log_error "无法连接到Kubernetes集群"
        exit 1
    fi
    
    # 检查Helm chart
    if [[ ! -d "$HELM_CHART_PATH" ]]; then
        log_error "Helm chart不存在: $HELM_CHART_PATH"
        exit 1
    fi
    
    log_success "依赖检查通过"
}

# 设置环境配置
setup_environment() {
    log_info "设置${ENVIRONMENT}环境配置..."
    
    case "$ENVIRONMENT" in
        dev)
            NAMESPACE="raft-storage-dev"
            KUBECONFIG_FILE="${HOME}/.kube/config-dev"
            VALUES_FILE="${SCRIPT_DIR}/environments/dev/values.yaml"
            ;;
        test)
            NAMESPACE="raft-storage-test"
            KUBECONFIG_FILE="${HOME}/.kube/config-test"
            VALUES_FILE="${SCRIPT_DIR}/environments/test/values.yaml"
            ;;
        prod)
            NAMESPACE="raft-storage-prod"
            KUBECONFIG_FILE="${HOME}/.kube/config-prod"
            VALUES_FILE="${SCRIPT_DIR}/environments/prod/values.yaml"
            ;;
    esac
    
    # 设置kubeconfig
    if [[ -f "$KUBECONFIG_FILE" ]]; then
        export KUBECONFIG="$KUBECONFIG_FILE"
        log_info "使用kubeconfig: $KUBECONFIG_FILE"
    else
        log_warning "未找到专用kubeconfig文件: $KUBECONFIG_FILE"
        log_warning "使用默认kubeconfig"
    fi
    
    # 检查values文件
    if [[ ! -f "$VALUES_FILE" ]]; then
        log_error "Values文件不存在: $VALUES_FILE"
        exit 1
    fi
    
    log_success "环境配置完成"
}

# 验证镜像版本
validate_version() {
    if [[ "$ROLLBACK" == true ]]; then
        return 0
    fi
    
    log_info "验证镜像版本: $VERSION"
    
    # 检查镜像是否存在
    local images=(
        "harbor.example.com/raft-storage/api:$VERSION"
        "harbor.example.com/raft-storage/raft-core:$VERSION"
    )
    
    for image in "${images[@]}"; do
        if ! docker manifest inspect "$image" &> /dev/null; then
            log_error "镜像不存在或无法访问: $image"
            exit 1
        fi
    done
    
    log_success "镜像版本验证通过"
}

# 备份当前部署
backup_current_deployment() {
    log_info "备份当前部署配置..."
    
    local backup_dir="${SCRIPT_DIR}/backups/${ENVIRONMENT}"
    local timestamp=$(date +"%Y%m%d_%H%M%S")
    local backup_file="${backup_dir}/backup_${timestamp}.yaml"
    
    mkdir -p "$backup_dir"
    
    # 获取当前Helm release信息
    if helm list -n "$NAMESPACE" | grep -q "raft-storage"; then
        helm get values raft-storage -n "$NAMESPACE" > "$backup_file"
        log_success "备份保存到: $backup_file"
    else
        log_warning "未找到现有部署，跳过备份"
    fi
}

# 执行部署
deploy() {
    log_info "开始部署到${ENVIRONMENT}环境..."
    
    # 构建Helm命令
    local helm_cmd="helm upgrade --install raft-storage $HELM_CHART_PATH"
    helm_cmd+=" --namespace $NAMESPACE"
    helm_cmd+=" --create-namespace"
    helm_cmd+=" --values $VALUES_FILE"
    helm_cmd+=" --timeout $TIMEOUT"
    helm_cmd+=" --wait"
    
    # 如果不是回滚，设置镜像版本
    if [[ "$ROLLBACK" == false ]]; then
        helm_cmd+=" --set global.imageTag=$VERSION"
        helm_cmd+=" --set api.image.tag=$VERSION"
        helm_cmd+=" --set raftCore.image.tag=$VERSION"
    fi
    
    # 生产环境额外检查
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        helm_cmd+=" --atomic"
    fi
    
    # 干运行模式
    if [[ "$DRY_RUN" == true ]]; then
        helm_cmd+=" --dry-run"
        log_info "执行干运行..."
    fi
    
    log_info "执行命令: $helm_cmd"
    
    # 执行部署
    if eval "$helm_cmd"; then
        log_success "Helm部署成功"
    else
        log_error "Helm部署失败"
        exit 1
    fi
}

# 执行回滚
rollback() {
    log_info "回滚${ENVIRONMENT}环境..."
    
    # 获取上一个版本
    local last_revision=$(helm history raft-storage -n "$NAMESPACE" --max 2 -o json | jq -r '.[0].revision')
    
    if [[ -z "$last_revision" || "$last_revision" == "null" ]]; then
        log_error "无法找到可回滚的版本"
        exit 1
    fi
    
    log_info "回滚到版本: $last_revision"
    
    local rollback_cmd="helm rollback raft-storage $last_revision"
    rollback_cmd+=" --namespace $NAMESPACE"
    rollback_cmd+=" --timeout $TIMEOUT"
    rollback_cmd+=" --wait"
    
    if [[ "$DRY_RUN" == true ]]; then
        rollback_cmd+=" --dry-run"
        log_info "执行回滚干运行..."
    fi
    
    log_info "执行命令: $rollback_cmd"
    
    if eval "$rollback_cmd"; then
        log_success "回滚成功"
    else
        log_error "回滚失败"
        exit 1
    fi
}

# 验证部署
verify_deployment() {
    if [[ "$DRY_RUN" == true ]]; then
        return 0
    fi
    
    log_info "验证部署状态..."
    
    # 等待Pod就绪
    log_info "等待Pod就绪..."
    if ! kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=raft-storage -n "$NAMESPACE" --timeout="$TIMEOUT"; then
        log_error "Pod未能在规定时间内就绪"
        exit 1
    fi
    
    # 检查服务状态
    log_info "检查服务状态..."
    kubectl get pods,svc,ingress -n "$NAMESPACE"
    
    # 健康检查
    if [[ "$ENVIRONMENT" != "prod" ]]; then
        local health_url
        case "$ENVIRONMENT" in
            dev)
                health_url="http://dev.raft-storage.local/actuator/health"
                ;;
            test)
                health_url="http://test.raft-storage.local/actuator/health"
                ;;
        esac
        
        log_info "执行健康检查: $health_url"
        if curl -f "$health_url" &> /dev/null; then
            log_success "健康检查通过"
        else
            log_warning "健康检查失败，请手动验证"
        fi
    fi
    
    log_success "部署验证完成"
}

# 清理函数
cleanup() {
    if [[ "$DEBUG" == true ]]; then
        set +x
    fi
}

# 主函数
main() {
    trap cleanup EXIT
    
    log_info "开始部署流程..."
    log_info "环境: $ENVIRONMENT"
    if [[ "$ROLLBACK" == false ]]; then
        log_info "版本: $VERSION"
    else
        log_info "操作: 回滚"
    fi
    
    # 确认部署（生产环境或非强制模式）
    if [[ "$ENVIRONMENT" == "prod" || "$FORCE" == false ]] && [[ "$DRY_RUN" == false ]]; then
        echo -n "确认要部署到${ENVIRONMENT}环境吗? (y/N): "
        read -r confirm
        if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
            log_info "部署已取消"
            exit 0
        fi
    fi
    
    # 执行部署流程
    check_dependencies
    setup_environment
    
    if [[ "$ROLLBACK" == false ]]; then
        validate_version
    fi
    
    backup_current_deployment
    
    if [[ "$ROLLBACK" == true ]]; then
        rollback
    else
        deploy
    fi
    
    verify_deployment
    
    log_success "部署流程完成！"
    
    # 显示部署信息
    if [[ "$DRY_RUN" == false ]]; then
        echo
        log_info "部署信息:"
        helm list -n "$NAMESPACE"
        echo
        log_info "访问地址:"
        case "$ENVIRONMENT" in
            dev)
                echo "  开发环境: http://dev.raft-storage.local"
                ;;
            test)
                echo "  测试环境: http://test.raft-storage.local"
                ;;
            prod)
                echo "  生产环境: https://raft-storage.example.com"
                ;;
        esac
    fi
}

# 解析参数并执行主函数
parse_args "$@"
main 