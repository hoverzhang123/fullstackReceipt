/**
 * 认证工具函数
 * 处理登录、注册、登出等认证操作
 */

import { supabase } from './supabase'

export type AuthError = {
  message: string
}

// 邮箱密码登录
export async function signInWithEmail(email: string, password: string) {
  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password,
  })

  if (error) {
    throw new Error(error.message)
  }

  return data
}

// 邮箱密码注册
export async function signUpWithEmail(email: string, password: string) {
  const { data, error } = await supabase.auth.signUp({
    email,
    password,
  })

  if (error) {
    throw new Error(error.message)
  }

  return data
}

// 登出
export async function signOut() {
  const { error } = await supabase.auth.signOut()
  
  if (error) {
    throw new Error(error.message)
  }
}

// 获取当前会话
export async function getCurrentSession() {
  const { data: { session }, error } = await supabase.auth.getSession()
  
  if (error) {
    throw new Error(error.message)
  }

  return session
}

// 获取当前用户
export async function getCurrentUser() {
  const { data: { user }, error } = await supabase.auth.getUser()
  
  if (error) {
    throw new Error(error.message)
  }

  return user
} 