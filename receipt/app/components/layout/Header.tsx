'use client'

/**
 * 顶部导航栏组件
 * 包含认证状态和用户操作
 */

import { useEffect, useState } from 'react'
import { getCurrentUser, signOut } from '@/lib/auth'
import { useRouter } from 'next/navigation'
import Link from 'next/link'

export default function Header() {
  const [user, setUser] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const router = useRouter()

  useEffect(() => {
    async function loadUser() {
      try {
        const user = await getCurrentUser()
        setUser(user)
      } catch (error) {
        console.error('Error loading user:', error)
      } finally {
        setLoading(false)
      }
    }

    loadUser()
  }, [])

  const handleSignOut = async () => {
    try {
      await signOut()
      setUser(null)
      router.push('/')
    } catch (error) {
      console.error('Error signing out:', error)
    }
  }

  return (
    <header className="bg-white shadow">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
        <div className="flex justify-between items-center">
          {/* Logo */}
          <Link href="/" className="flex items-center">
            <h1 className="text-2xl font-bold text-gray-900">Recipe</h1>
          </Link>

          {/* 搜索框 */}
          <div className="hidden md:flex flex-1 max-w-lg mx-8">
            <div className="w-full">
              <div className="relative">
                <input
                  type="text"
                  className="w-full px-4 py-2 rounded-lg border border-gray-300 focus:outline-none focus:ring-2 focus:ring-gray-200"
                  placeholder="搜索食谱..."
                />
                <button className="absolute right-3 top-2.5 text-gray-400">
                  🔍
                </button>
              </div>
            </div>
          </div>

          {/* 用户操作按钮 */}
          <div className="flex items-center space-x-4">
            {loading ? (
              <div className="text-gray-500">加载中...</div>
            ) : user ? (
              <div className="flex items-center space-x-4">
                <span className="text-gray-700">欢迎, {user.email}</span>
                <button
                  onClick={handleSignOut}
                  className="text-gray-600 hover:text-gray-900"
                >
                  退出
                </button>
              </div>
            ) : (
              <>
                <Link
                  href="/auth/login"
                  className="text-gray-600 hover:text-gray-900"
                >
                  登录
                </Link>
                <Link
                  href="/auth/signup"
                  className="px-4 py-2 bg-black text-white rounded-lg hover:bg-gray-800 transition-colors"
                >
                  注册
                </Link>
              </>
            )}
          </div>
        </div>
      </div>
    </header>
  )
} 