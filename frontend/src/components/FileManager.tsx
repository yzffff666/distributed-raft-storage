import React, { useState, useEffect } from 'react';
import {
  Card,
  Upload,
  Table,
  Button,
  Input,
  Space,
  message,
  Modal,
  Tag,
  Progress,
  Tooltip,
  Popconfirm,
  Typography,
  Row,
  Col,
  Statistic
} from 'antd';
import {
  UploadOutlined,
  SearchOutlined,
  DeleteOutlined,
  DownloadOutlined,
  FileOutlined,
  FolderOutlined,
  ReloadOutlined,
  InfoCircleOutlined
} from '@ant-design/icons';
import type { UploadProps, TableColumnsType } from 'antd';
import apiService from '../services/api';

const { Search } = Input;
const { Text } = Typography;

interface FileItem {
  key: string;
  name: string;
  size: number;
  type: string;
  uploadTime: number;
  lastAccess?: number;
  downloadCount?: number;
}

interface FileManagerProps {
  height?: number;
}

const FileManager: React.FC<FileManagerProps> = ({ height = 600 }) => {
  const [files, setFiles] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [fileStats, setFileStats] = useState({
    totalFiles: 0,
    totalSize: 0,
    totalDownloads: 0
  });

  useEffect(() => {
    loadFiles();
  }, []);

  const loadFiles = async () => {
    setLoading(true);
    try {
      // 获取所有键
      const keysResponse = await apiService.getAllKeys();
      if (keysResponse.success) {
        // 模拟文件信息（实际应该从后端获取完整的文件元数据）
        const fileItems: FileItem[] = keysResponse.data
          .filter(key => key.includes('file:') || key.includes('upload:'))
          .map(key => ({
            key,
            name: key.split(':').pop() || key,
            size: Math.floor(Math.random() * 10000000), // 模拟文件大小
            type: getFileType(key),
            uploadTime: Date.now() - Math.floor(Math.random() * 86400000 * 30), // 30天内随机时间
            lastAccess: Date.now() - Math.floor(Math.random() * 86400000 * 7), // 7天内随机时间
            downloadCount: Math.floor(Math.random() * 100)
          }));

        setFiles(fileItems);
        
        // 计算统计信息
        const stats = {
          totalFiles: fileItems.length,
          totalSize: fileItems.reduce((sum, file) => sum + file.size, 0),
          totalDownloads: fileItems.reduce((sum, file) => sum + (file.downloadCount || 0), 0)
        };
        setFileStats(stats);
      }
    } catch (error) {
      message.error('加载文件列表失败');
    } finally {
      setLoading(false);
    }
  };

  const getFileType = (filename: string): string => {
    const ext = filename.split('.').pop()?.toLowerCase();
    switch (ext) {
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'gif':
        return 'image';
      case 'pdf':
        return 'pdf';
      case 'doc':
      case 'docx':
        return 'document';
      case 'txt':
        return 'text';
      case 'zip':
      case 'rar':
        return 'archive';
      default:
        return 'file';
    }
  };

  const getFileIcon = (type: string) => {
    switch (type) {
      case 'image':
        return '🖼️';
      case 'pdf':
        return '📄';
      case 'document':
        return '📝';
      case 'text':
        return '📋';
      case 'archive':
        return '📦';
      default:
        return '📁';
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const handleUpload: UploadProps['customRequest'] = async (options) => {
    const { file, onSuccess, onError, onProgress } = options;
    
    setUploading(true);
    try {
      // 模拟上传进度
      for (let i = 0; i <= 100; i += 10) {
        setTimeout(() => {
          onProgress?.({ percent: i });
        }, i * 10);
      }

      // 实际上传文件
      const uploadFile = file as File;
      const response = await apiService.uploadFile(uploadFile);
      
      if (response.success) {
        message.success(`${uploadFile.name} 上传成功`);
        onSuccess?.(response.data);
        loadFiles(); // 重新加载文件列表
      } else {
        throw new Error(response.message);
      }
    } catch (error: any) {
      message.error(`上传失败: ${error.message}`);
      onError?.(error);
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (file: FileItem) => {
    try {
      const blob = await apiService.downloadFile(file.key);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = file.name;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      message.success(`${file.name} 下载成功`);
    } catch (error) {
      message.error('下载失败');
    }
  };

  const handleDelete = async (file: FileItem) => {
    try {
      const response = await apiService.deleteData(file.key);
      if (response.success) {
        message.success(`${file.name} 删除成功`);
        loadFiles();
      } else {
        throw new Error(response.message);
      }
    } catch (error: any) {
      message.error(`删除失败: ${error.message}`);
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请选择要删除的文件');
      return;
    }

    Modal.confirm({
      title: '确认删除',
      content: `确定要删除选中的 ${selectedRowKeys.length} 个文件吗？`,
      onOk: async () => {
        try {
          for (const key of selectedRowKeys) {
            await apiService.deleteData(key as string);
          }
          message.success(`成功删除 ${selectedRowKeys.length} 个文件`);
          setSelectedRowKeys([]);
          loadFiles();
        } catch (error) {
          message.error('批量删除失败');
        }
      }
    });
  };

  const handleSearch = async (value: string) => {
    if (!value.trim()) {
      loadFiles();
      return;
    }

    setLoading(true);
    try {
      const response = await apiService.searchKeys(value);
      if (response.success) {
        const fileItems: FileItem[] = response.data
          .filter(key => key.includes('file:') || key.includes('upload:'))
          .map(key => ({
            key,
            name: key.split(':').pop() || key,
            size: Math.floor(Math.random() * 10000000),
            type: getFileType(key),
            uploadTime: Date.now() - Math.floor(Math.random() * 86400000 * 30),
            lastAccess: Date.now() - Math.floor(Math.random() * 86400000 * 7),
            downloadCount: Math.floor(Math.random() * 100)
          }));
        setFiles(fileItems);
      }
    } catch (error) {
      message.error('搜索失败');
    } finally {
      setLoading(false);
    }
  };

  const columns: TableColumnsType<FileItem> = [
    {
      title: '文件名',
      dataIndex: 'name',
      key: 'name',
      render: (text, record) => (
        <Space>
          <span style={{ fontSize: 16 }}>{getFileIcon(record.type)}</span>
          <Text strong>{text}</Text>
          <Tag color="blue">{record.type}</Tag>
        </Space>
      ),
      sorter: (a, b) => a.name.localeCompare(b.name),
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      render: (size) => formatFileSize(size),
      sorter: (a, b) => a.size - b.size,
      width: 100,
    },
    {
      title: '上传时间',
      dataIndex: 'uploadTime',
      key: 'uploadTime',
      render: (time) => new Date(time).toLocaleString(),
      sorter: (a, b) => a.uploadTime - b.uploadTime,
      width: 150,
    },
    {
      title: '最后访问',
      dataIndex: 'lastAccess',
      key: 'lastAccess',
      render: (time) => time ? new Date(time).toLocaleString() : '未访问',
      width: 150,
    },
    {
      title: '下载次数',
      dataIndex: 'downloadCount',
      key: 'downloadCount',
      render: (count) => <Tag color="green">{count || 0}</Tag>,
      sorter: (a, b) => (a.downloadCount || 0) - (b.downloadCount || 0),
      width: 100,
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space>
          <Tooltip title="下载">
            <Button
              type="text"
              icon={<DownloadOutlined />}
              onClick={() => handleDownload(record)}
            />
          </Tooltip>
          <Tooltip title="删除">
            <Popconfirm
              title="确定要删除这个文件吗？"
              onConfirm={() => handleDelete(record)}
              okText="确定"
              cancelText="取消"
            >
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
              />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
    },
  ];

  const rowSelection = {
    selectedRowKeys,
    onChange: setSelectedRowKeys,
  };

  return (
    <div style={{ height }}>
      {/* 统计信息 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card size="small">
            <Statistic
              title="文件总数"
              value={fileStats.totalFiles}
              prefix={<FileOutlined />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small">
            <Statistic
              title="总大小"
              value={formatFileSize(fileStats.totalSize)}
              prefix={<FolderOutlined />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small">
            <Statistic
              title="总下载数"
              value={fileStats.totalDownloads}
              prefix={<DownloadOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="文件管理"
        extra={
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={loadFiles}
              loading={loading}
            >
              刷新
            </Button>
            <Upload
              customRequest={handleUpload}
              showUploadList={false}
              multiple
            >
              <Button
                type="primary"
                icon={<UploadOutlined />}
                loading={uploading}
              >
                上传文件
              </Button>
            </Upload>
          </Space>
        }
      >
        {/* 搜索和批量操作 */}
        <div style={{ marginBottom: 16 }}>
          <Row gutter={16} align="middle">
            <Col flex="auto">
              <Search
                placeholder="搜索文件名..."
                allowClear
                enterButton={<SearchOutlined />}
                onSearch={handleSearch}
                onChange={(e) => setSearchText(e.target.value)}
              />
            </Col>
            <Col>
              <Space>
                {selectedRowKeys.length > 0 && (
                  <Button
                    danger
                    icon={<DeleteOutlined />}
                    onClick={handleBatchDelete}
                  >
                    批量删除 ({selectedRowKeys.length})
                  </Button>
                )}
              </Space>
            </Col>
          </Row>
        </div>

        {/* 文件列表 */}
        <Table
          columns={columns}
          dataSource={files}
          rowSelection={rowSelection}
          loading={loading}
          pagination={{
            total: files.length,
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) =>
              `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
          }}
          scroll={{ y: height - 280 }}
          size="small"
        />
      </Card>
    </div>
  );
};

export default FileManager;
