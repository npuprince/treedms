export type Role = 'ADMIN' | 'VISITOR'

export interface LoginResponse {
  token: string
  username: string
  role: Role
}

export interface UserInfo {
  username: string
  role: Role
}

export interface DepartmentNode {
  id: number
  pid: number | null
  department: string
  sortOrder: number
  encrypted: boolean
  favorite: boolean
  children: DepartmentNode[]
}

export interface FavoriteDepartment {
  id: number
  pid: number | null
  department: string
  path: string
  createdAt: string
}

export interface FileItem {
  id: number
  departmentId: number
  originalName: string
  contentType: string
  size: number
  uploader: string
  createdAt: string
  pinned: boolean
  sortOrder: number
}
