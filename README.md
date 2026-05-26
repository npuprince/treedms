# TreeDMS

一个小型在线文档管理系统，采用 Spring Boot + MyBatis Plus + MySQL + Vue3 + 本地磁盘存储。

## 目录结构

```text
TreeDMS/
  backend/      Spring Boot 后端
  frontend/     Vue3 前端
  database/     MySQL 初始化脚本
```

## 数据库初始化

1. 确认 MySQL 已启动。
2. 执行初始化脚本：

```bash
mysql -u root -p < database/init.sql
```

`database/init.sql` 会创建缺失的库和表，并补齐示例部门数据；不会清空已有文件记录。

默认库名为 `treedms`。脚本会创建部门表 `sys_department` 和文件表 `biz_file`，并初始化部门树：

```text
root
  a
    f
      e
    d
      g
  b
```

## 后端启动

后端默认连接：

```text
jdbc:mysql://localhost:3306/treedms
username: root
password: ******
```

如需修改，可通过环境变量覆盖：

```powershell
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="你的密码"
$env:TREEDMS_STORAGE_ROOT="D:\TreeDMS\backend\storage\files"
cd backend
.\mvnw.cmd spring-boot:run
```

后端接口地址：`http://localhost:8080/api`

## 前端启动

```powershell
cd frontend
npm install
npm run dev
```

本项目已在 `frontend/.npmrc` 中把 npm 缓存固定到当前项目的 `.npm-cache`，避免写入全局缓存目录。

前端地址：`http://localhost:5173`

## 默认账号

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 访客 | `visitor` | `visitor123` |

管理员可上传、删除、预览、下载文件；访客只能预览和下载文件。

在线预览支持图片、PDF、文本文件和 Word `.docx`。旧版 Word `.doc` 文件暂不支持在线渲染，可下载查看或转换为 `.docx` 后上传。

管理员登录后，还可以在左侧部门树节点上右键打开目录维护菜单：

- 添加子部门：在当前选中部门下创建子部门。
- 重命名：修改当前选中部门名称。
- 删除部门：删除当前选中的叶子部门。根部门、包含子部门的部门、包含文件的部门不能删除。
- 目录加密：管理员可对非根目录加密或取消加密。加密目录及其子目录对访客不可见，访客也不能通过接口访问其中的文件。
- 拖拽部门：拖动左侧部门节点可以调整同级排序，或移动到其他部门下面。
- 移动文件：拖动右侧文件名到左侧部门节点，可以把文件移动到对应部门。
- 文件置顶：管理员可在文件行操作区置顶或取消置顶，置顶文件会排在当前部门文件列表前面。
- 回收站：管理员删除文件后，文件会进入回收站，可在右侧切换到回收站并恢复文件。

访客登录后，可以在左侧部门树节点上右键收藏可见目录，并在右侧“我的收藏”页面查看、打开或取消收藏目录。

## 本地存储

上传文件默认保存到后端运行目录下的 `storage/files`。数据库只保存文件元数据和相对路径，真实文件保存在本地磁盘。
