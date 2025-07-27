# fullstackReceipt

git remote set-url origin git@github.com-hoverzhang123:hoverzhang123/fullstackReceipt.git

# npx create-next-app@latest
# run website
npm run dev


#Add lines below for each request:
Product Requirements Document (PRD)

Project Name: Recipe Sharing Platform

Objective: To build a web application where users can upload and browse recipes, leveraging Next.js for the frontend and Supabase for the backend (database and authentication).

Core Features

User Authentication:

Users can sign up, log in, and log out using Supabase authentication.

Password reset functionality.

Recipe Management:

Users can upload recipes with details like title, ingredients, steps, and an optional image.

Users can browse all uploaded recipes.

Users can view a detailed page for each recipe.

Search and Filtering:

Basic keyword search functionality.

Filter recipes by category (e.g., appetizers, desserts, main courses).

Responsive Design:

Ensure the platform is mobile-friendly and provides a seamless user experience on all devices.

Technical Stack

Frontend:

Framework: Next.js

Styling: Tailwind CSS or CSS Modules

Here's a text version of the provided image:

2. Backend:

Database: Supabase PostgreSQL

Authentication: Supabase Auth

3. Deployment:

Vercel for hosting the Next.js application

Questions to ask AI:

1.
I just created a superbase project and enabled authentication for email only. 

Can you help me with the database table setup?

2. 
Just to show you how this works initially before we start creating tons of tables.
asek AI: Can you create only two tables to start (No categories). 

So the first is a profiles table(id, username, full name, created at, updated at)

Second is the recipes table(id, created at,User ID title, ingredients, instructions, cooking time,Difficulty,category)

3.
asek AI: 
We have created 2 database table in superbase [profiles and recipes]. Please reference this image so you know what fields have in each of the two data tabels. 

Now lets go ahead and start the initial setup of superbase in this project. Please walk me through this. 


4. Follow instructions from step 3:

5. ask AI:
we have populated .env file inside our  codebase. 

can you just implement a quick test on our home page to make sure we are connected to superbase?