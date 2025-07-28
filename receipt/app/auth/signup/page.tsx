/**
 * 注册页面
 */

import SignUpForm from '@/app/components/auth/SignUpForm'

export default function SignUpPage() {
  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <h1 className="text-center text-3xl font-bold tracking-tight text-gray-900 mb-8">
          Recipe
        </h1>
        <SignUpForm />
      </div>
    </div>
  )
} 