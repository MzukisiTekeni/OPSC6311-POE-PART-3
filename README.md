# BudgetBuddy

App's APK Files link: https://github.com/MzukisiTekeni/OPSC6311-POE-PART-3/tree/main/APK

YouTube video Link: https://youtu.be/TbAPwAgJmPM

App Name: BudgetBuddy
An android application designed to help users to take control of their personal finances. It allows users to
track daily expenses, set monthly budgets, monitor savings goals and gain insight into their spending
habits through statistics and budget health reports. BudgetBuddy can help users save money, pay off
debt and track their spending habits while making it easy to understand with the use of gamification
concepts
Purpose of the app
To make managing personal finances less overwhelming with a tool that can:
• Log and categorize expenses
• Set and track monthly budgets
• Create and monitor savings goals
• Receive budget health feedback
• View spending statistics
• Manage their profile and notification preferences
Design Considerations
The app is was built with Kotlin and using Android SDK, with activity-based navigation with 16 dedicated
screens. It uses local storage, Room database for persistent offline-first data storage. It has a push
notification feature that support the user on their financial journey with reminders. The orientation of
the app is portrait locked for a consistent experience more over the app allows for the user to have
either a dark/light screen for ease on their eyes while in use.
GitHub and GitHub Actions
This app is hosted on GitHub for version control and collaboration GitHub Actions automates the build
process by compiling the app and produces the APK. We’ve also set up an automated CI/CD pipeline that
acts as a continuous quality check that automates the app, runs Junit tests to catch errors early and to
ensure the codebase is completely healthy and working, and automatically builds the final APK once
everything passes
Screenshots of each screen:
The app has 16 screens including; Welcome, Login, Register, Forgot Password, Dashboard, Add Expense,
Vie Expenses, Expense Categories, Monthly Budget, Log Budget, Savings Goals, Log Savings Goals,
Statistics, Budget Health, Notifications, and Profile
