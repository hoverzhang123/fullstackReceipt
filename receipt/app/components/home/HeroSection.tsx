/**
 * 主页欢迎区域组件
 * 包含主标题和行动按钮
 */

export default function HeroSection() {
  return (
    <div className="text-center py-16 md:py-24 bg-gradient-to-b from-gray-50">
      <h1 className="text-4xl md:text-5xl lg:text-6xl font-bold text-gray-900 mb-6">
        分享你的美食故事
      </h1>
      <p className="text-xl text-gray-600 mb-8 max-w-2xl mx-auto px-4">
        在这里发现美味食谱，分享烹饪技巧，与其他美食爱好者交流
      </p>
      <button className="px-8 py-3 bg-black text-white rounded-lg text-lg font-semibold hover:bg-gray-800 transition-colors">
        开始探索
      </button>
    </div>
  )
} 