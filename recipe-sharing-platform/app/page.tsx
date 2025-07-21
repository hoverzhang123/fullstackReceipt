import Image from "next/image";
import Header from './components/Header';

export default function Home() {
  return (
    <div className="min-h-screen bg-background text-foreground font-sans">
      <Header />
      <main className="flex flex-col items-center justify-center text-center p-6">
        <div className="w-full max-w-2xl flex flex-col items-center gap-8 py-12 md:py-24">
          <h1 className="text-4xl sm:text-5xl md:text-6xl font-extrabold tracking-tight">
            Welcome to RecipeShare
          </h1>
          <p className="text-lg sm:text-xl text-gray-600 dark:text-gray-300">
            Discover, share, and enjoy delicious recipes from around the world.
          </p>
          <div className="mt-6">
            <button
              className="bg-foreground text-background px-8 py-3 rounded-full font-semibold text-lg shadow-md hover:bg-gray-800 dark:hover:bg-gray-200 dark:hover:text-black transition-colors duration-300"
              disabled
            >
              Get Started
            </button>
          </div>
        </div>
      </main>
      <footer className="w-full text-center p-4 text-xs text-gray-400">
        &copy; {new Date().getFullYear()} Recipe Sharing Platform. All rights reserved.
      </footer>
    </div>
  );
}
