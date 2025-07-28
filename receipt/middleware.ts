/**
 * 认证中间件
 * 处理用户会话和认证状态
 */

import { createMiddlewareClient } from '@supabase/auth-helpers-nextjs'
import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

export async function middleware(req: NextRequest) {
  const res = NextResponse.next()
  const supabase = createMiddlewareClient({ req, res })

  // 刷新用户会话
  await supabase.auth.getSession()

  return res
} 