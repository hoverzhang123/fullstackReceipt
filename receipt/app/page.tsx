'use client'

/**
 * 主页
 * 组合所有组件，构建完整页面
 */

import Header from './components/layout/Header'
import Footer from './components/layout/Footer'
import HeroSection from './components/home/HeroSection'

export default function Home() {
  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      
      <main className="flex-grow">
        <HeroSection />

        {/* 特色食谱区域 */}
        <section className="py-16 bg-white">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <h2 className="text-3xl font-bold text-center mb-12">精选食谱</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
              {[1, 2, 3].map((i) => (
                <div key={i} className="bg-white rounded-lg shadow-lg overflow-hidden">
                  <div className="h-48 bg-gray-200" />
                  <div className="p-6">
                    <h3 className="text-xl font-semibold mb-2">美味食谱 {i}</h3>
                    <p className="text-gray-600 mb-4">这是一道简单但美味的食谱...</p>
                    <div className="flex justify-between items-center">
                      <span className="text-sm text-gray-500">烹饪时间：30分钟</span>
                      <button className="text-black hover:text-gray-600">
                        查看详情 →
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* 分类区域 */}
        <section className="py-16 bg-gray-50">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <h2 className="text-3xl font-bold text-center mb-12">浏览分类</h2>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
              {['早餐', '午餐', '晚餐', '甜点'].map((category) => (
                <button
                  key={category}
                  className="p-8 bg-white rounded-lg shadow-md hover:shadow-lg transition-shadow text-center"
                >
                  <span className="text-lg font-medium">{category}</span>
                </button>
              ))}
            </div>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  )
}
