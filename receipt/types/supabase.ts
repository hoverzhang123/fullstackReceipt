/**
 * Supabase 数据库类型定义
 * 根据数据库表结构定义 TypeScript 类型
 */

export type Database = {
  public: {
    Tables: {
      profiles: {
        Row: {
          id: string
          username: string
          full_name: string | null
          created_at: string
          updated_at: string
        }
        Insert: {
          id: string
          username: string
          full_name?: string | null
          created_at?: string
          updated_at?: string
        }
        Update: {
          id?: string
          username?: string
          full_name?: string | null
          created_at?: string
          updated_at?: string
        }
      }
      recipes: {
        Row: {
          id: string
          created_at: string
          user_id: string
          title: string
          description: string | null
          ingredients: string
          instructions: string
          cooking_time: number | null
          difficulty: string | null
          category: string
        }
        Insert: {
          id?: string
          created_at?: string
          user_id: string
          title: string
          description?: string | null
          ingredients: string
          instructions: string
          cooking_time?: number | null
          difficulty?: string | null
          category: string
        }
        Update: {
          id?: string
          created_at?: string
          user_id?: string
          title?: string
          description?: string | null
          ingredients?: string
          instructions?: string
          cooking_time?: number | null
          difficulty?: string | null
          category?: string
        }
      }
    }
  }
} 