pipeline {
    agent any
    
    environment {
        // Maven配置
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
        
        // Docker配置
        DOCKER_REGISTRY = 'your-registry.com'
        DOCKER_REPO = 'raft-storage'
        
        // Kubernetes配置
        KUBECONFIG = credentials('kubeconfig')
        
        // SonarQube配置
        SONAR_HOST_URL = 'http://sonarqube:9000'
        SONAR_TOKEN = credentials('sonar-token')
        
        // Harbor配置
        HARBOR_URL = 'harbor.example.com'
        HARBOR_PROJECT = 'raft-storage'
        HARBOR_CREDENTIALS = credentials('harbor-credentials')
    }
    
    options {
        // 构建保留策略
        buildDiscarder(logRotator(numToKeepStr: '10'))
        // 超时设置
        timeout(time: 1, unit: 'HOURS')
        // 并发构建设置
        disableConcurrentBuilds()
        // 时间戳
        timestamps()
    }
    
    triggers {
        // Git提交触发
        githubPush()
        // 定时构建 (每天凌晨2点)
        cron('H 2 * * *')
    }
    
    stages {
        stage('🔍 代码检出') {
            steps {
                echo '检出代码...'
                checkout scm
                
                script {
                    // 获取Git信息
                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    env.BUILD_VERSION = "${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                }
                
                echo "构建版本: ${env.BUILD_VERSION}"
            }
        }
        
        stage('🛠️ 环境准备') {
            parallel {
                stage('Maven缓存') {
                    steps {
                        echo '设置Maven缓存...'
                        sh '''
                            mkdir -p .m2/repository
                            ls -la .m2/repository || true
                        '''
                    }
                }
                
                stage('Docker环境') {
                    steps {
                        echo '检查Docker环境...'
                        sh '''
                            docker --version
                            docker info
                        '''
                    }
                }
            }
        }
        
        stage('📋 代码验证') {
            steps {
                echo '代码格式检查和基础验证...'
                sh '''
                    mvn clean validate compile -DskipTests
                '''
            }
        }
        
        stage('🧪 测试执行') {
            parallel {
                stage('单元测试') {
                    steps {
                        echo '执行单元测试...'
                        sh '''
                            mvn test -Dtest=*Test
                        '''
                    }
                    post {
                        always {
                            // 发布测试报告
                            publishTestResults testResultsPattern: '**/target/surefire-reports/TEST-*.xml'
                            
                            // 发布覆盖率报告
                            publishCoverage adapters: [
                                jacocoAdapter('**/target/site/jacoco/jacoco.xml')
                            ], sourceFileResolver: sourceFiles('STORE_LAST_BUILD')
                        }
                    }
                }
                
                stage('集成测试') {
                    steps {
                        echo '执行集成测试...'
                        sh '''
                            # 启动测试依赖服务
                            docker-compose -f docker-compose.test.yml up -d redis
                            
                            # 等待服务启动
                            sleep 10
                            
                            # 执行集成测试
                            mvn verify -Dskip.unit.tests=true
                        '''
                    }
                    post {
                        always {
                            // 清理测试环境
                            sh 'docker-compose -f docker-compose.test.yml down || true'
                            
                            // 发布集成测试报告
                            publishTestResults testResultsPattern: '**/target/failsafe-reports/TEST-*.xml'
                        }
                    }
                }
            }
        }
        
        stage('📊 代码质量检查') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    changeRequest()
                }
            }
            steps {
                echo '执行SonarQube代码质量分析...'
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        mvn sonar:sonar \\
                            -Dsonar.projectKey=raft-storage \\
                            -Dsonar.projectName="Raft Storage System" \\
                            -Dsonar.host.url=${SONAR_HOST_URL} \\
                            -Dsonar.login=${SONAR_TOKEN}
                    '''
                }
                
                // 等待质量门检查
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        
        stage('🏗️ 构建应用') {
            steps {
                echo '构建应用程序...'
                sh '''
                    mvn clean package -DskipTests
                '''
                
                // 归档构建产物
                archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
            }
        }
        
        stage('🐳 构建镜像') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            parallel {
                stage('API服务镜像') {
                    steps {
                        script {
                            echo '构建API服务Docker镜像...'
                            def apiImage = docker.build(
                                "${HARBOR_URL}/${HARBOR_PROJECT}/api:${env.BUILD_VERSION}",
                                "-f spring-boot-api/Dockerfile spring-boot-api/"
                            )
                            
                            // 推送到Harbor
                            docker.withRegistry("https://${HARBOR_URL}", HARBOR_CREDENTIALS) {
                                apiImage.push()
                                apiImage.push('latest')
                            }
                        }
                    }
                }
                
                stage('Raft核心镜像') {
                    steps {
                        script {
                            echo '构建Raft核心服务Docker镜像...'
                            def coreImage = docker.build(
                                "${HARBOR_URL}/${HARBOR_PROJECT}/raft-core:${env.BUILD_VERSION}",
                                "-f distribute-java-core/Dockerfile distribute-java-core/"
                            )
                            
                            // 推送到Harbor
                            docker.withRegistry("https://${HARBOR_URL}", HARBOR_CREDENTIALS) {
                                coreImage.push()
                                coreImage.push('latest')
                            }
                        }
                    }
                }
            }
        }
        
        stage('🔒 安全扫描') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            parallel {
                stage('镜像安全扫描') {
                    steps {
                        echo '执行Docker镜像安全扫描...'
                        sh '''
                            # 使用Trivy扫描镜像
                            docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \\
                                aquasec/trivy:latest image \\
                                --exit-code 0 \\
                                --severity HIGH,CRITICAL \\
                                --format json \\
                                -o trivy-report.json \\
                                ${HARBOR_URL}/${HARBOR_PROJECT}/api:${BUILD_VERSION}
                        '''
                        
                        // 发布安全扫描报告
                        publishHTML([
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: '.',
                            reportFiles: 'trivy-report.json',
                            reportName: 'Trivy Security Report'
                        ])
                    }
                }
                
                stage('依赖安全检查') {
                    steps {
                        echo '执行依赖安全检查...'
                        sh '''
                            mvn org.owasp:dependency-check-maven:check
                        '''
                        
                        // 发布依赖检查报告
                        publishHTML([
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'target',
                            reportFiles: 'dependency-check-report.html',
                            reportName: 'OWASP Dependency Check Report'
                        ])
                    }
                }
            }
        }
        
        stage('🚀 部署') {
            parallel {
                stage('部署到开发环境') {
                    when {
                        branch 'develop'
                    }
                    steps {
                        echo '部署到开发环境...'
                        script {
                            kubernetesDeploy(
                                configs: 'k8s/environments/dev/**/*.yaml',
                                kubeconfigId: 'kubeconfig-dev'
                            )
                        }
                        
                        // 验证部署
                        sh '''
                            kubectl get pods -n raft-storage-dev
                            kubectl rollout status deployment/raft-api -n raft-storage-dev
                        '''
                    }
                }
                
                stage('部署到测试环境') {
                    when {
                        branch 'main'
                    }
                    steps {
                        echo '部署到测试环境...'
                        script {
                            kubernetesDeploy(
                                configs: 'k8s/environments/test/**/*.yaml',
                                kubeconfigId: 'kubeconfig-test'
                            )
                        }
                        
                        // 验证部署
                        sh '''
                            kubectl get pods -n raft-storage-test
                            kubectl rollout status deployment/raft-api -n raft-storage-test
                        '''
                    }
                }
            }
        }
        
        stage('🧪 部署后测试') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            parallel {
                stage('健康检查') {
                    steps {
                        echo '执行健康检查...'
                        script {
                            if (env.BRANCH_NAME == 'develop') {
                                sh '''
                                    curl -f http://dev.raft-storage.local/actuator/health || exit 1
                                '''
                            } else if (env.BRANCH_NAME == 'main') {
                                sh '''
                                    curl -f http://test.raft-storage.local/actuator/health || exit 1
                                '''
                            }
                        }
                    }
                }
                
                stage('接口测试') {
                    steps {
                        echo '执行接口测试...'
                        sh '''
                            # 使用Newman执行Postman测试集合
                            newman run tests/postman/raft-storage-api.json \\
                                --environment tests/postman/test-env.json \\
                                --reporters cli,junit \\
                                --reporter-junit-export newman-results.xml
                        '''
                    }
                    post {
                        always {
                            publishTestResults testResultsPattern: 'newman-results.xml'
                        }
                    }
                }
            }
        }
        
        stage('📈 性能测试') {
            when {
                branch 'main'
            }
            steps {
                echo '执行性能测试...'
                sh '''
                    # 使用K6执行性能测试
                    docker run --rm -v $(pwd)/scripts:/scripts \\
                        grafana/k6:latest run \\
                        --out json=performance-results.json \\
                        /scripts/performance-test.js
                '''
                
                // 发布性能测试报告
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: '.',
                    reportFiles: 'performance-results.json',
                    reportName: 'K6 Performance Report'
                ])
            }
        }
    }
    
    post {
        always {
            echo '清理工作空间...'
            
            // 清理Docker镜像
            sh '''
                docker system prune -f || true
                docker image prune -f || true
            '''
            
            // 发送构建通知
            script {
                def status = currentBuild.currentResult ?: 'SUCCESS'
                def color = status == 'SUCCESS' ? 'good' : 'danger'
                def message = """
                    *${status}*: Job `${env.JOB_NAME}` build `${env.BUILD_NUMBER}`
                    Branch: `${env.BRANCH_NAME}`
                    Commit: `${env.GIT_COMMIT_SHORT}`
                    Duration: ${currentBuild.durationString}
                    <${env.BUILD_URL}|View Build>
                """.stripIndent()
                
                // 发送到Slack (需要配置Slack插件)
                slackSend(
                    channel: '#ci-cd',
                    color: color,
                    message: message
                )
            }
        }
        
        success {
            echo '✅ 构建成功!'
            
            // 成功时的额外操作
            script {
                if (env.BRANCH_NAME == 'main') {
                    // 创建Git标签
                    sh "git tag -a v${env.BUILD_VERSION} -m 'Release version ${env.BUILD_VERSION}'"
                    sh "git push origin v${env.BUILD_VERSION}"
                }
            }
        }
        
        failure {
            echo '❌ 构建失败!'
            
            // 失败时收集诊断信息
            sh '''
                echo "=== 系统信息 ==="
                df -h
                free -m
                docker ps -a
                
                echo "=== 最近的日志 ==="
                tail -100 /var/log/jenkins/jenkins.log || true
            '''
        }
        
        unstable {
            echo '⚠️ 构建不稳定!'
        }
        
        cleanup {
            echo '🧹 最终清理...'
            cleanWs()
        }
    }
} 