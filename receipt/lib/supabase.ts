/**
 * Supabase 客户端配置
 * 创建与 Supabase 的连接实例
 */

import { createClient } from '@supabase/supabase-js'
import { Database } from '@/types/supabase'

// 从环境变量获取 Supabase 配置
const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL!
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!

// 创建类型安全的 Supabase 客户端
export const supabase = createClient<Database>(supabaseUrl, supabaseAnonKey) 