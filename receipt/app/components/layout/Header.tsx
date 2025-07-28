/**
 * 顶部导航栏组件
 * 包含 Logo、搜索框和用户操作按钮
 */

export default function Header() {
  return (
    <header className="bg-white shadow">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
        <div className="flex justify-between items-center">
          {/* Logo */}
          <div className="flex items-center">
            <h1 className="text-2xl font-bold text-gray-900">Recipe</h1>
          </div>

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
            <button className="text-gray-600 hover:text-gray-900">登录</button>
            <button className="px-4 py-2 bg-black text-white rounded-lg hover:bg-gray-800 transition-colors">
              注册
            </button>
          </div>
        </div>
      </div>
    </header>
  )
} 