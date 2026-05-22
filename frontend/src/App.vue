<template>
  <div v-if="isAuthed" class="app-shell" @click="closeDepartmentContextMenu">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">T</span>
        <div>
          <strong>TreeDMS</strong>
          <span>文档管理系统</span>
        </div>
      </div>

      <div class="userbar">
        <el-tag :type="roleTagType" effect="plain">{{ roleLabel }}</el-tag>
        <span class="username">{{ auth.username }}</span>
        <el-tooltip content="退出登录" placement="bottom">
          <el-button :icon="SwitchButton" circle @click="handleLogout" />
        </el-tooltip>
      </div>
    </header>

    <main class="workbench">
      <aside class="department-pane">
        <div class="pane-head">
          <span>部门目录</span>
          <div class="dept-actions">
            <el-tooltip content="刷新部门" placement="bottom">
              <el-button :icon="Refresh" circle @click="loadDepartments()" />
            </el-tooltip>
          </div>
        </div>

        <el-input
          v-model="departmentKeyword"
          class="department-search"
          clearable
          placeholder="搜索部门"
          :prefix-icon="Search"
        />

        <el-tree
          ref="treeRef"
          class="department-tree"
          :data="departments"
          node-key="id"
          default-expand-all
          highlight-current
          :draggable="isAdmin"
          :props="treeProps"
          :filter-node-method="filterDepartment"
          :allow-drag="allowDepartmentDrag"
          :allow-drop="allowDepartmentDrop"
          :current-node-key="selectedDepartment?.id"
          @node-click="handleDepartmentClick"
          @node-contextmenu="openDepartmentContextMenu"
          @node-drop="handleDepartmentDrop"
        >
          <template #default="{ data }">
            <span
              class="tree-node"
              :class="{ 'tree-node-file-drop': fileDropDepartmentId === data.id }"
              @dragenter.prevent="handleFileDragEnter(data)"
              @dragover.prevent="handleFileDragOver"
              @dragleave="handleFileDragLeave(data)"
              @drop.prevent.stop="dropFileOnDepartment(data)"
            >
              <el-icon><FolderOpened /></el-icon>
              <span>{{ data.department }}</span>
              <el-icon v-if="data.encrypted" class="tree-state-icon"><Lock /></el-icon>
              <el-icon v-else-if="data.favorite" class="tree-state-icon favorite"><StarFilled /></el-icon>
            </span>
          </template>
        </el-tree>
      </aside>

      <section class="file-pane">
        <div class="file-toolbar">
          <div class="file-title">
            <h1>{{ viewTitle }}</h1>
            <span v-if="activeView === 'recycle'">可恢复已删除的文件</span>
            <span v-else-if="activeView === 'favorites'">查看已收藏的目录</span>
            <span v-else-if="selectedDepartment">部门 ID: {{ selectedDepartment.id }}</span>
          </div>

          <div class="toolbar-actions">
            <el-segmented v-model="activeView" :options="viewOptions" @change="handleViewChange" />
            <el-button :icon="Refresh" @click="refreshCurrentView">刷新</el-button>
            <el-upload
              v-if="isAdmin && activeView === 'files'"
              :show-file-list="false"
              :http-request="uploadFile"
              :disabled="!selectedDepartment"
            >
              <el-button type="primary" :icon="Upload" :disabled="!selectedDepartment">上传</el-button>
            </el-upload>
          </div>
        </div>

        <div class="table-tools">
          <el-input
            v-model="fileKeyword"
            clearable
            :placeholder="searchPlaceholder"
            :prefix-icon="Search"
          />
        </div>

        <el-table
          v-if="activeView === 'favorites'"
          v-loading="fileLoading"
          class="file-table"
          :data="filteredFavorites"
          height="100%"
          empty-text="暂无收藏目录"
        >
          <el-table-column label="目录" min-width="180" prop="department" />
          <el-table-column label="路径" min-width="260" prop="path" />
          <el-table-column label="收藏时间" width="180">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="132" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-tooltip content="打开目录" placement="top">
                  <el-button :icon="FolderOpened" circle type="primary" @click="openFavoriteDepartment(row)" />
                </el-tooltip>
                <el-tooltip content="取消收藏" placement="top">
                  <el-button :icon="StarFilled" circle type="warning" @click="removeFavoriteFromList(row)" />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <el-table
          v-else
          v-loading="fileLoading"
          class="file-table"
          :data="filteredFiles"
          height="100%"
          :empty-text="activeView === 'recycle' ? '回收站暂无文件' : '当前部门暂无文件'"
        >
          <el-table-column label="文件名" min-width="300">
            <template #default="{ row }">
              <div
                class="file-name"
                :class="{ 'file-name-pinned': row.pinned }"
                :draggable="isAdmin && activeView === 'files'"
                @dragstart="startFileDrag(row, $event)"
                @dragend="endFileDrag"
              >
                <el-icon v-if="isAdmin && activeView === 'files'" class="drag-handle"><Rank /></el-icon>
                <el-icon><Document /></el-icon>
                <span>{{ row.originalName }}</span>
                <el-tag v-if="activeView === 'files' && row.pinned" size="small" type="warning" effect="plain">
                  置顶
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">
                v{{ row.versionNo || 1 }} / {{ row.versionCount || 1 }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型" min-width="160">
            <template #default="{ row }">
              <span class="muted">{{ row.contentType || 'unknown' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="120">
            <template #default="{ row }">{{ formatSize(row.size) }}</template>
          </el-table-column>
          <el-table-column label="上传人" width="120" prop="uploader" />
          <el-table-column v-if="activeView === 'recycle'" label="原部门" width="120">
            <template #default="{ row }">ID: {{ row.departmentId }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="180">
            <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="258" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-tooltip v-if="isAdmin && activeView === 'recycle'" content="恢复" placement="top">
                  <el-button :icon="RefreshLeft" circle type="success" @click="restoreFile(row)" />
                </el-tooltip>
                <el-tooltip
                  v-if="isAdmin && activeView === 'files'"
                  :content="row.pinned ? '取消置顶' : '置顶'"
                  placement="top"
                >
                  <el-button
                    :icon="row.pinned ? StarFilled : Star"
                    circle
                    :type="row.pinned ? 'warning' : 'default'"
                    @click="toggleFilePin(row)"
                  />
                </el-tooltip>
                <el-tooltip v-if="activeView === 'files'" content="版本历史" placement="top">
                  <el-button :icon="Clock" circle @click="openVersionDialog(row)" />
                </el-tooltip>
                <el-tooltip v-if="activeView === 'files'" content="预览" placement="top">
                  <el-button :icon="View" circle @click="previewFile(row)" />
                </el-tooltip>
                <el-tooltip v-if="activeView === 'files'" content="下载" placement="top">
                  <el-button :icon="Download" circle @click="downloadFile(row)" />
                </el-tooltip>
                <el-tooltip v-if="isAdmin && activeView === 'files'" content="删除" placement="top">
                  <el-button :icon="Delete" circle type="danger" @click="deleteFile(row)" />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </main>

    <div
      v-if="departmentContextMenu.visible"
      class="context-menu"
      :style="{ left: `${departmentContextMenu.x}px`, top: `${departmentContextMenu.y}px` }"
      @click.stop
    >
      <template v-if="isAdmin">
        <button type="button" @click="runDepartmentMenuAction(openCreateDepartment)">
          <el-icon><Plus /></el-icon>
          <span>添加子部门</span>
        </button>
        <button type="button" @click="runDepartmentMenuAction(openRenameDepartment)">
          <el-icon><Edit /></el-icon>
          <span>重命名</span>
        </button>
        <button type="button" :disabled="!canDeleteContextDepartment" @click="runDepartmentMenuAction(deleteDepartment)">
          <el-icon><Delete /></el-icon>
          <span>删除</span>
        </button>
        <button
          type="button"
          :disabled="departmentContextMenu.target?.id === 0"
          @click="runDepartmentMenuAction(toggleDepartmentEncryption)"
        >
          <el-icon>
            <Unlock v-if="departmentContextMenu.target?.encrypted" />
            <Lock v-else />
          </el-icon>
          <span>{{ departmentContextMenu.target?.encrypted ? '取消加密' : '目录加密' }}</span>
        </button>
      </template>
      <template v-else>
        <button type="button" @click="runDepartmentMenuAction(toggleDepartmentFavorite)">
          <el-icon>
            <StarFilled v-if="departmentContextMenu.target?.favorite" />
            <Star v-else />
          </el-icon>
          <span>{{ departmentContextMenu.target?.favorite ? '取消收藏' : '收藏目录' }}</span>
        </button>
      </template>
    </div>

    <el-dialog
      v-model="preview.visible"
      class="preview-dialog"
      :title="preview.name"
      width="72%"
      top="6vh"
      @closed="clearPreview"
    >
      <div class="preview-body">
        <img v-if="preview.kind === 'image'" :src="preview.url" :alt="preview.name" />
        <iframe
          v-else-if="preview.kind === 'frame'"
          :src="preview.url"
          title="file preview"
        />
        <article v-else-if="preview.kind === 'docx'" class="word-preview" v-html="preview.html" />
        <div v-else class="preview-fallback">
          <el-icon><Document /></el-icon>
          <strong>{{ preview.message || '该类型不支持在线预览' }}</strong>
          <el-button type="primary" :icon="Download" @click="downloadCurrentPreview">下载</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="versionDialog.visible" class="version-dialog" :title="versionDialogTitle" width="720px">
      <el-table
        v-loading="versionDialog.loading"
        :data="versionDialog.versions"
        empty-text="暂无版本记录"
      >
        <el-table-column label="版本" width="96">
          <template #default="{ row }">
            <el-tag :type="row.current ? 'success' : 'info'" effect="plain">v{{ row.versionNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="上传人" width="120" prop="uploader" />
        <el-table-column label="上传时间" min-width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-tooltip content="预览此版本" placement="top">
                <el-button :icon="View" circle @click="previewVersion(row)" />
              </el-tooltip>
              <el-tooltip content="下载此版本" placement="top">
                <el-button :icon="Download" circle @click="downloadVersion(row)" />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="departmentDialog.visible"
      :title="departmentDialogTitle"
      width="420px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item v-if="departmentDialog.mode === 'create'" label="上级部门">
          <el-input :model-value="departmentDialog.parent?.department || '-'" disabled />
        </el-form-item>
        <el-form-item label="部门名称">
          <el-input
            v-model="departmentDialog.name"
            maxlength="100"
            show-word-limit
            autofocus
            @keyup.enter="submitDepartmentDialog"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="departmentDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="departmentDialog.loading" @click="submitDepartmentDialog">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>

  <div v-else class="login-page">
    <section class="login-panel">
      <div class="login-brand">
        <span class="brand-mark">T</span>
        <div>
          <strong>TreeDMS</strong>
          <span>企业业务文件管理</span>
        </div>
      </div>

      <el-segmented v-model="loginMode" :options="loginModeOptions" @change="applyLoginMode" />

      <el-form class="login-form" :model="loginForm" label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="loginForm.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button class="login-submit" type="primary" :loading="loginLoading" @click="handleLogin">
          登录
        </el-button>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import type { UploadRequestOptions } from 'element-plus'
import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
import {
  Delete,
  Document,
  Download,
  Edit,
  FolderOpened,
  Lock,
  Plus,
  Rank,
  Clock,
  Refresh,
  RefreshLeft,
  Search,
  Star,
  StarFilled,
  SwitchButton,
  Unlock,
  Upload,
  View
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { api, apiMessage, TOKEN_KEY, USER_KEY } from './api'
import type {
  DepartmentNode,
  FavoriteDepartment,
  FileItem,
  FileVersionItem,
  LoginResponse,
  Role,
  UserInfo
} from './types'

const treeProps = {
  label: 'department',
  children: 'children'
}

type UploadError = Parameters<NonNullable<UploadRequestOptions['onError']>>[0]
type PreviewableFile = Pick<FileItem, 'originalName' | 'contentType'>

const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  username: '',
  role: 'VISITOR' as Role
})

const loginMode = ref<Role>('ADMIN')
const loginModeOptions = [
  { label: '管理员', value: 'ADMIN' },
  { label: '访客', value: 'VISITOR' }
]
const loginForm = reactive({
  username: 'admin',
  password: 'admin123'
})
const loginLoading = ref(false)

const departments = ref<DepartmentNode[]>([])
const selectedDepartment = ref<DepartmentNode | null>(null)
const departmentKeyword = ref('')
const treeRef = ref<InstanceType<typeof ElTree>>()
const departmentDialog = reactive({
  visible: false,
  mode: 'create' as 'create' | 'rename',
  parent: null as DepartmentNode | null,
  target: null as DepartmentNode | null,
  name: '',
  loading: false
})
const departmentContextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  target: null as DepartmentNode | null
})

const files = ref<FileItem[]>([])
const recycledFiles = ref<FileItem[]>([])
const favoriteDepartments = ref<FavoriteDepartment[]>([])
const fileKeyword = ref('')
const fileLoading = ref(false)
const draggedFile = ref<FileItem | null>(null)
const fileDropDepartmentId = ref<number | null>(null)
const activeView = ref<'files' | 'recycle' | 'favorites'>('files')
const viewOptions = computed(() =>
  isAdmin.value
    ? [
        { label: '文件管理', value: 'files' },
        { label: '回收站', value: 'recycle' }
      ]
    : [
        { label: '文件管理', value: 'files' },
        { label: '我的收藏', value: 'favorites' }
      ]
)

const preview = reactive({
  visible: false,
  id: 0,
  name: '',
  url: '',
  html: '',
  message: '',
  downloadUrl: '',
  downloadName: '',
  kind: 'other' as 'image' | 'frame' | 'docx' | 'other'
})
const versionDialog = reactive({
  visible: false,
  loading: false,
  file: null as FileItem | null,
  versions: [] as FileVersionItem[]
})

const isAuthed = computed(() => Boolean(auth.token))
const isAdmin = computed(() => auth.role === 'ADMIN')
const roleLabel = computed(() => (isAdmin.value ? '管理员' : '访客'))
const roleTagType = computed(() => (isAdmin.value ? 'success' : 'info'))
const canDeleteContextDepartment = computed(() => {
  const department = departmentContextMenu.target
  return Boolean(department && department.id !== 0 && (!department.children || department.children.length === 0))
})
const departmentDialogTitle = computed(() =>
  departmentDialog.mode === 'create' ? '添加子部门' : '修改部门名称'
)
const versionDialogTitle = computed(() =>
  versionDialog.file ? `版本历史 - ${versionDialog.file.originalName}` : '版本历史'
)
const viewTitle = computed(() => {
  if (activeView.value === 'recycle') {
    return '回收站'
  }
  if (activeView.value === 'favorites') {
    return '我的收藏'
  }
  return selectedDepartment.value?.department || '请选择部门'
})
const searchPlaceholder = computed(() => {
  if (activeView.value === 'recycle') {
    return '搜索回收站文件'
  }
  if (activeView.value === 'favorites') {
    return '搜索收藏目录'
  }
  return '搜索文件名'
})
const filteredFiles = computed(() => {
  const keyword = fileKeyword.value.trim().toLowerCase()
  const source = activeView.value === 'recycle' ? recycledFiles.value : files.value
  if (!keyword) {
    return source
  }
  return source.filter((file) => file.originalName.toLowerCase().includes(keyword))
})
const filteredFavorites = computed(() => {
  const keyword = fileKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return favoriteDepartments.value
  }
  return favoriteDepartments.value.filter((item) =>
    `${item.department} ${item.path}`.toLowerCase().includes(keyword)
  )
})

watch(departmentKeyword, (keyword) => {
  treeRef.value?.filter(keyword)
})

onMounted(async () => {
  restoreCachedUser()
  if (auth.token) {
    try {
      const { data } = await api.get<UserInfo>('/auth/me')
      setUser(data)
      await loadDepartments()
    } catch {
      clearAuth()
    }
  }
})

function applyLoginMode(value: string | number | boolean) {
  if (value === 'VISITOR') {
    loginForm.username = 'visitor'
    loginForm.password = 'visitor123'
    return
  }
  loginForm.username = 'admin'
  loginForm.password = 'admin123'
}

async function handleLogin() {
  loginLoading.value = true
  try {
    const { data } = await api.post<LoginResponse>('/auth/login', loginForm)
    auth.token = data.token
    localStorage.setItem(TOKEN_KEY, data.token)
    setUser(data)
    ElMessage.success('登录成功')
    await loadDepartments()
  } catch (error) {
    ElMessage.error(apiMessage(error))
  } finally {
    loginLoading.value = false
  }
}

async function handleLogout() {
  try {
    await api.post('/auth/logout')
  } catch {
    // Local logout should still complete when the server session is already gone.
  }
  clearAuth()
}

async function loadDepartments(preferredId?: number) {
  try {
    const { data } = await api.get<DepartmentNode[]>('/departments/tree')
    departments.value = data
    const nextId = preferredId ?? selectedDepartment.value?.id
    selectedDepartment.value = nextId ? findDepartmentById(data, nextId) || data[0] || null : data[0] || null
    await nextTick()
    if (selectedDepartment.value) {
      treeRef.value?.setCurrentKey(selectedDepartment.value.id)
      await refreshCurrentView()
    } else {
      files.value = []
    }
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

async function loadFiles() {
  if (!selectedDepartment.value) {
    files.value = []
    return
  }
  fileLoading.value = true
  try {
    const { data } = await api.get<FileItem[]>('/files', {
      params: { departmentId: selectedDepartment.value.id }
    })
    files.value = data
  } catch (error) {
    ElMessage.error(apiMessage(error))
  } finally {
    fileLoading.value = false
  }
}

async function loadRecycleFiles() {
  if (!isAdmin.value) {
    recycledFiles.value = []
    return
  }
  fileLoading.value = true
  try {
    const { data } = await api.get<FileItem[]>('/files/recycle')
    recycledFiles.value = data
  } catch (error) {
    ElMessage.error(apiMessage(error))
  } finally {
    fileLoading.value = false
  }
}

async function loadFavoriteDepartments() {
  if (isAdmin.value) {
    favoriteDepartments.value = []
    return
  }
  fileLoading.value = true
  try {
    const { data } = await api.get<FavoriteDepartment[]>('/departments/favorites')
    favoriteDepartments.value = data
  } catch (error) {
    ElMessage.error(apiMessage(error))
  } finally {
    fileLoading.value = false
  }
}

async function refreshCurrentView() {
  if (activeView.value === 'recycle') {
    await loadRecycleFiles()
    return
  }
  if (activeView.value === 'favorites') {
    await loadFavoriteDepartments()
    return
  }
  await loadFiles()
}

function handleViewChange() {
  fileKeyword.value = ''
  void refreshCurrentView()
}

function handleDepartmentClick(data: DepartmentNode) {
  closeDepartmentContextMenu()
  selectedDepartment.value = data
  if (activeView.value === 'files') {
    void loadFiles()
  }
}

function openDepartmentContextMenu(event: Event, data: DepartmentNode) {
  const mouseEvent = event as MouseEvent
  event.preventDefault()
  event.stopPropagation()
  selectedDepartment.value = data
  treeRef.value?.setCurrentKey(data.id)
  departmentContextMenu.target = data
  departmentContextMenu.x = Math.max(12, Math.min(mouseEvent.clientX, window.innerWidth - 190))
  departmentContextMenu.y = Math.max(12, Math.min(mouseEvent.clientY, window.innerHeight - 180))
  departmentContextMenu.visible = true
}

function closeDepartmentContextMenu() {
  departmentContextMenu.visible = false
}

function runDepartmentMenuAction(action: () => void | Promise<void>) {
  if (departmentContextMenu.target) {
    selectedDepartment.value = departmentContextMenu.target
    treeRef.value?.setCurrentKey(departmentContextMenu.target.id)
  }
  closeDepartmentContextMenu()
  void action()
}

async function toggleDepartmentEncryption() {
  const department = selectedDepartment.value
  if (!department || department.id === 0) {
    return
  }

  try {
    await api.put(`/departments/${department.id}/encrypt`, {
      encrypted: !department.encrypted
    })
    ElMessage.success(department.encrypted ? '目录已取消加密' : '目录已加密')
    await loadDepartments(department.id)
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

async function toggleDepartmentFavorite() {
  const department = selectedDepartment.value
  if (!department) {
    return
  }

  try {
    if (department.favorite) {
      await api.delete(`/departments/${department.id}/favorite`)
      ElMessage.success('已取消收藏')
    } else {
      await api.post(`/departments/${department.id}/favorite`)
      ElMessage.success('目录已收藏')
    }
    await loadDepartments(department.id)
    if (activeView.value === 'favorites') {
      await loadFavoriteDepartments()
    }
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

async function openFavoriteDepartment(row: FavoriteDepartment) {
  const target = findDepartmentById(departments.value, row.id)
  if (!target) {
    ElMessage.warning('该目录当前不可见')
    await loadFavoriteDepartments()
    return
  }
  activeView.value = 'files'
  selectedDepartment.value = target
  await nextTick()
  treeRef.value?.setCurrentKey(target.id)
  await loadFiles()
}

async function removeFavoriteFromList(row: FavoriteDepartment) {
  try {
    await api.delete(`/departments/${row.id}/favorite`)
    ElMessage.success('已取消收藏')
    await loadFavoriteDepartments()
    await loadDepartments(selectedDepartment.value?.id)
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

function allowDepartmentDrag(node: any) {
  return isAdmin.value && !draggedFile.value && node.data?.id !== 0
}

function allowDepartmentDrop(draggingNode: any, dropNode: any, type: string) {
  if (!isAdmin.value || draggedFile.value || draggingNode.data?.id === 0) {
    return false
  }
  if (draggingNode.data?.id === dropNode.data?.id) {
    return false
  }
  if (dropNode.data?.id === 0 && type !== 'inner') {
    return false
  }
  return true
}

async function handleDepartmentDrop(draggingNode: any) {
  const movingId = Number(draggingNode.data?.id)
  const placement = findParentAndSiblingIds(departments.value, movingId)
  if (!placement || placement.parentId === null) {
    await loadDepartments(movingId)
    return
  }

  try {
    await api.put(`/departments/${movingId}/move`, {
      parentId: placement.parentId,
      orderedIds: placement.orderedIds
    })
    ElMessage.success('部门位置已更新')
    await loadDepartments(movingId)
  } catch (error) {
    ElMessage.error(apiMessage(error))
    await loadDepartments(selectedDepartment.value?.id)
  }
}

function startFileDrag(row: FileItem, event: DragEvent) {
  if (!isAdmin.value || activeView.value !== 'files' || !event.dataTransfer) {
    return
  }
  draggedFile.value = row
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(row.id))
}

function endFileDrag() {
  draggedFile.value = null
  fileDropDepartmentId.value = null
}

function handleFileDragEnter(data: DepartmentNode) {
  if (draggedFile.value && draggedFile.value.departmentId !== data.id) {
    fileDropDepartmentId.value = data.id
  }
}

function handleFileDragOver(event: DragEvent) {
  if (draggedFile.value && event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function handleFileDragLeave(data: DepartmentNode) {
  if (fileDropDepartmentId.value === data.id) {
    fileDropDepartmentId.value = null
  }
}

async function dropFileOnDepartment(data: DepartmentNode) {
  const file = draggedFile.value
  if (!file || file.departmentId === data.id) {
    endFileDrag()
    return
  }

  try {
    const { data: movedFile } = await api.put<FileItem>(`/files/${file.id}/move`, {
      departmentId: data.id
    })
    ElMessage.success('文件已移动')
    selectedDepartment.value = findDepartmentById(departments.value, movedFile.departmentId) || data
    await nextTick()
    treeRef.value?.setCurrentKey(selectedDepartment.value.id)
    await loadFiles()
  } catch (error) {
    ElMessage.error(apiMessage(error))
  } finally {
    endFileDrag()
  }
}

function openCreateDepartment() {
  if (!selectedDepartment.value) {
    ElMessage.warning('请选择上级部门')
    return
  }
  departmentDialog.mode = 'create'
  departmentDialog.parent = selectedDepartment.value
  departmentDialog.target = null
  departmentDialog.name = ''
  departmentDialog.visible = true
}

function openRenameDepartment() {
  if (!selectedDepartment.value) {
    ElMessage.warning('请选择部门')
    return
  }
  departmentDialog.mode = 'rename'
  departmentDialog.parent = null
  departmentDialog.target = selectedDepartment.value
  departmentDialog.name = selectedDepartment.value.department
  departmentDialog.visible = true
}

async function submitDepartmentDialog() {
  const name = departmentDialog.name.trim()
  if (!name) {
    ElMessage.warning('请输入部门名称')
    return
  }

  departmentDialog.loading = true
  try {
    if (departmentDialog.mode === 'create') {
      const parent = departmentDialog.parent
      if (!parent) {
        return
      }
      const { data } = await api.post<DepartmentNode>(`/departments/${parent.id}/children`, {
        department: name
      })
      departmentDialog.visible = false
      ElMessage.success('子部门已添加')
      await loadDepartments(data.id)
      return
    }

    const target = departmentDialog.target
    if (!target) {
      return
    }
    const { data } = await api.put<DepartmentNode>(`/departments/${target.id}`, {
      department: name
    })
    departmentDialog.visible = false
    ElMessage.success('部门名称已修改')
    await loadDepartments(data.id)
  } catch (error) {
    ElMessage.error(apiMessage(error))
  } finally {
    departmentDialog.loading = false
  }
}

async function deleteDepartment() {
  const department = selectedDepartment.value
  if (!department) {
    return
  }
  if (department.id === 0) {
    ElMessage.warning('根部门不能删除')
    return
  }
  if (department.children?.length) {
    ElMessage.warning('请先删除该部门下的子部门')
    return
  }

  try {
    await ElMessageBox.confirm(`删除部门「${department.department}」？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await api.delete(`/departments/${department.id}`)
    ElMessage.success('部门已删除')
    await loadDepartments(department.pid ?? 0)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(apiMessage(error))
    }
  }
}

function filterDepartment(keyword: string, data: any) {
  if (!keyword) {
    return true
  }
  return String(data.department || '').toLowerCase().includes(keyword.toLowerCase())
}

function findDepartmentById(nodes: DepartmentNode[], id: number): DepartmentNode | null {
  for (const node of nodes) {
    if (node.id === id) {
      return node
    }
    const found = findDepartmentById(node.children || [], id)
    if (found) {
      return found
    }
  }
  return null
}

function findParentAndSiblingIds(
  nodes: DepartmentNode[],
  id: number,
  parentId: number | null = null
): { parentId: number | null; orderedIds: number[] } | null {
  if (nodes.some((node) => node.id === id)) {
    return {
      parentId,
      orderedIds: nodes.map((node) => node.id)
    }
  }

  for (const node of nodes) {
    const found = findParentAndSiblingIds(node.children || [], id, node.id)
    if (found) {
      return found
    }
  }
  return null
}

async function uploadFile(options: UploadRequestOptions) {
  if (!selectedDepartment.value) {
    ElMessage.warning('请选择部门')
    return
  }

  const form = new FormData()
  form.append('departmentId', String(selectedDepartment.value.id))
  form.append('file', options.file)

  try {
    const { data } = await api.post<FileItem>('/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    options.onSuccess?.({})
    ElMessage.success((data.versionCount || 1) > 1 ? `已上传为 v${data.versionNo}` : '上传成功')
    await loadFiles()
  } catch (error) {
    options.onError?.(error as UploadError)
    ElMessage.error(apiMessage(error))
  }
}

async function previewFile(row: FileItem) {
  await previewResource(row, `/files/${row.id}/preview`, `/files/${row.id}/download`, row.originalName)
}

async function previewResource(
  file: PreviewableFile,
  previewUrl: string,
  downloadUrl: string,
  downloadName: string
) {
  try {
    revokePreviewUrl()
    preview.html = ''
    preview.message = ''
    const { data } = await api.get<Blob>(previewUrl, { responseType: 'blob' })
    const kind = previewKind(file)
    const blob = new Blob([data], { type: previewMimeType(file, data) })

    preview.id = 0
    preview.name = downloadName
    preview.downloadUrl = downloadUrl
    preview.downloadName = downloadName

    if (kind === 'docx') {
      const [{ default: mammoth }, { default: DOMPurify }] = await Promise.all([
        import('mammoth'),
        import('dompurify')
      ])
      const result = await mammoth.convertToHtml({
        arrayBuffer: await blob.arrayBuffer()
      })
      preview.kind = 'docx'
      preview.html = DOMPurify.sanitize(result.value || '<p>文档没有可预览内容</p>')
      preview.visible = true
      return
    }

    preview.kind = kind === 'legacy-word' ? 'other' : kind
    preview.message =
      kind === 'legacy-word'
        ? '旧版 .doc 暂不支持在线预览，请转换为 .docx 后上传'
        : '该类型不支持在线预览'
    preview.url = kind === 'image' || kind === 'frame' ? URL.createObjectURL(blob) : ''
    preview.visible = true
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

async function downloadFile(row: FileItem) {
  try {
    const { data } = await api.get<Blob>(`/files/${row.id}/download`, { responseType: 'blob' })
    saveBlob(data, row.originalName)
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

async function openVersionDialog(row: FileItem) {
  versionDialog.visible = true
  versionDialog.file = row
  versionDialog.versions = []
  await loadFileVersions()
}

async function loadFileVersions() {
  if (!versionDialog.file) {
    return
  }
  versionDialog.loading = true
  try {
    const { data } = await api.get<FileVersionItem[]>(`/files/${versionDialog.file.id}/versions`)
    versionDialog.versions = data
  } catch (error) {
    ElMessage.error(apiMessage(error))
  } finally {
    versionDialog.loading = false
  }
}

async function previewVersion(row: FileVersionItem) {
  const file = versionDialog.file
  if (!file) {
    return
  }
  const name = versionedFilename(file.originalName, row.versionNo)
  await previewResource(
    { originalName: file.originalName, contentType: row.contentType },
    `/files/${file.id}/versions/${row.id}/preview`,
    `/files/${file.id}/versions/${row.id}/download`,
    name
  )
}

async function downloadVersion(row: FileVersionItem) {
  const file = versionDialog.file
  if (!file) {
    return
  }
  try {
    const { data } = await api.get<Blob>(`/files/${file.id}/versions/${row.id}/download`, { responseType: 'blob' })
    saveBlob(data, versionedFilename(file.originalName, row.versionNo))
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

async function toggleFilePin(row: FileItem) {
  try {
    await api.put<FileItem>(`/files/${row.id}/pin`, {
      pinned: !row.pinned
    })
    ElMessage.success(row.pinned ? '已取消置顶' : '文件已置顶')
    await loadFiles()
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

async function restoreFile(row: FileItem) {
  try {
    await api.put<FileItem>(`/files/${row.id}/restore`)
    ElMessage.success('文件已恢复')
    await loadRecycleFiles()
    if (selectedDepartment.value?.id === row.departmentId) {
      await loadFiles()
    }
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

async function deleteFile(row: FileItem) {
  try {
    await ElMessageBox.confirm(`删除文件「${row.originalName}」？文件将进入回收站。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await api.delete(`/files/${row.id}`)
    ElMessage.success('文件已移入回收站')
    await loadFiles()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(apiMessage(error))
    }
  }
}

async function downloadCurrentPreview() {
  if (!preview.downloadUrl) {
    return
  }
  try {
    const { data } = await api.get<Blob>(preview.downloadUrl, { responseType: 'blob' })
    saveBlob(data, preview.downloadName || preview.name)
  } catch (error) {
    ElMessage.error(apiMessage(error))
  }
}

function previewKind(file: PreviewableFile) {
  const contentType = (file.contentType || '').toLowerCase()
  const extension = fileExtension(file.originalName)
  if (contentType.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'].includes(extension)) {
    return 'image'
  }
  if (contentType === 'application/pdf' || extension === 'pdf' || contentType.startsWith('text/')) {
    return 'frame'
  }
  if (
    contentType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
    extension === 'docx'
  ) {
    return 'docx'
  }
  if (contentType === 'application/msword' || extension === 'doc') {
    return 'legacy-word'
  }
  return 'other'
}

function previewMimeType(file: PreviewableFile, blob: Blob) {
  const contentType = (file.contentType || blob.type || '').toLowerCase()
  const extension = fileExtension(file.originalName)
  if (extension === 'pdf') {
    return 'application/pdf'
  }
  if (extension === 'docx') {
    return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  }
  if (contentType && contentType !== 'application/octet-stream') {
    return contentType
  }
  if (['jpg', 'jpeg'].includes(extension)) {
    return 'image/jpeg'
  }
  if (extension === 'png') {
    return 'image/png'
  }
  if (extension === 'txt') {
    return 'text/plain'
  }
  return 'application/octet-stream'
}

function fileExtension(filename: string) {
  const index = filename.lastIndexOf('.')
  return index >= 0 ? filename.slice(index + 1).toLowerCase() : ''
}

function versionedFilename(filename: string, versionNo: number) {
  const suffix = `-v${versionNo || 1}`
  const index = filename.lastIndexOf('.')
  if (index <= 0) {
    return `${filename}${suffix}`
  }
  return `${filename.slice(0, index)}${suffix}${filename.slice(index)}`
}

function clearPreview() {
  revokePreviewUrl()
  preview.id = 0
  preview.name = ''
  preview.html = ''
  preview.message = ''
  preview.downloadUrl = ''
  preview.downloadName = ''
  preview.kind = 'other'
}

function revokePreviewUrl() {
  if (preview.url) {
    URL.revokeObjectURL(preview.url)
    preview.url = ''
  }
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function formatSize(size: number) {
  if (!Number.isFinite(size)) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function formatDate(value: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

function setUser(user: UserInfo) {
  auth.username = user.username
  auth.role = user.role
  if (user.role === 'ADMIN' && activeView.value === 'favorites') {
    activeView.value = 'files'
  }
  if (user.role === 'VISITOR' && activeView.value === 'recycle') {
    activeView.value = 'files'
  }
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

function restoreCachedUser() {
  const cached = localStorage.getItem(USER_KEY)
  if (!cached) {
    return
  }
  try {
    setUser(JSON.parse(cached) as UserInfo)
  } catch {
    localStorage.removeItem(USER_KEY)
  }
}

function clearAuth() {
  closeDepartmentContextMenu()
  departmentContextMenu.target = null
  versionDialog.visible = false
  versionDialog.file = null
  versionDialog.versions = []
  auth.token = ''
  auth.username = ''
  auth.role = 'VISITOR'
  departments.value = []
  files.value = []
  recycledFiles.value = []
  favoriteDepartments.value = []
  selectedDepartment.value = null
  activeView.value = 'files'
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
</script>
