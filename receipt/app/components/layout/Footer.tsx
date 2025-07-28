/**
 * 页脚组件
 * 包含版权信息和快速链接
 */

export default function Footer() {
  return (
    <footer className="bg-white border-t">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* 左侧 Logo 和简介 */}
          <div>
            <h3 className="text-lg font-semibold mb-4">Recipe</h3>
            <p className="text-gray-600">
              分享美食，连接世界
            </p>
          </div>

          {/* 中间链接 */}
          <div>
            <h4 className="text-sm font-semibold mb-4">快速链接</h4>
            <ul className="space-y-2">
              <li><a href="#" className="text-gray-600 hover:text-gray-900">关于我们</a></li>
              <li><a href="#" className="text-gray-600 hover:text-gray-900">使用条款</a></li>
              <li><a href="#" className="text-gray-600 hover:text-gray-900">隐私政策</a></li>
            </ul>
          </div>

          {/* 右侧联系方式 */}
          <div>
            <h4 className="text-sm font-semibold mb-4">联系我们</h4>
            <ul className="space-y-2">
              <li className="text-gray-600">邮箱：contact@recipe.com</li>
              <li className="text-gray-600">微信：RecipeOfficial</li>
            </ul>
          </div>
        </div>

        {/* 版权信息 */}
        <div className="mt-8 pt-8 border-t text-center text-gray-500 text-sm">
          © {new Date().getFullYear()} Recipe 美食分享平台. 保留所有权利.
        </div>
      </div>
    </footer>
  )
} 