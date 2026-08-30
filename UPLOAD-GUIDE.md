# راهنمای آپلود Srvther به GitHub

## مرحله ۱: ساخت ریپو در GitHub
1. به https://github.com/new بروید
2. نام: srvther
3. Private یا Public
4. بدون README و gitignore (از قبل داریم)
5. Create repository بزنید

## مرحله ۲: Push به GitHub

`ash
cd C:\Users\Seyed\Documents\GitHub\srvther
git add .
git commit -m "Initial commit: Srvther v1.0.0"
git remote add origin https://github.com/seyed84s/srvther.git
git push -u origin main
`

## مرحله ۳: بیلد APK
- GitHub Actions به صورت خودکار APK میسازه
- یا از Android Studio بیلد بگیرید
