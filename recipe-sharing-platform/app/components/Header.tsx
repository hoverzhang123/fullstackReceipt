import Link from 'next/link';

const Header = () => {
  return (
    <header className="w-full bg-background border-b border-gray-200 dark:border-gray-800">
      <div className="container mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <div className="flex-shrink-0">
            <Link href="/" className="text-2xl font-bold text-foreground">
              RecipeShare
            </Link>
          </div>
          <nav className="hidden md:flex md:items-center md:space-x-8">
            <Link href="/browse" className="text-gray-500 hover:text-foreground dark:text-gray-400 dark:hover:text-white">
              Browse Recipes
            </Link>
            <Link href="/login" className="text-gray-500 hover:text-foreground dark:text-gray-400 dark:hover:text-white">
              Login
            </Link>
            <Link href="/signup" className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-gray-800 dark:hover:bg-gray-200 dark:hover:text-black">
              Sign Up
            </Link>
          </nav>
          <div className="md:hidden">
            {/* Mobile menu button will go here */}
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header; 