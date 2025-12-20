const fs = require('fs');
const path = require('path');

function copyFolderSync(source, target) {
  // 确保目标目录存在
  if (!fs.existsSync(target)) {
    fs.mkdirSync(target, { recursive: true });
  }
  
  // 读取源目录内容
  const files = fs.readdirSync(source);
  
  files.forEach(file => {
    const sourcePath = path.join(source, file);
    const targetPath = path.join(target, file);
    
    if (fs.statSync(sourcePath).isDirectory()) {
      // 递归复制子目录
      copyFolderSync(sourcePath, targetPath);
    } else {
      // 复制文件
      fs.copyFileSync(sourcePath, targetPath);
    }
  });
}

function getFileCount(dir) {
  let count = 0;
  const files = fs.readdirSync(dir);
  
  files.forEach(file => {
    const filePath = path.join(dir, file);
    if (fs.statSync(filePath).isDirectory()) {
      count += getFileCount(filePath);
    } else {
      count++;
    }
  });
  
  return count;
}

function main() {
  try {
    const sourceDir = path.resolve(__dirname, '../dist');
    const targetDir = path.resolve(__dirname, '../../src/main/resources/templates');
    
    console.log('🚀 Starting copy process...');
    console.log(`Source: ${sourceDir}`);
    console.log(`Target: ${targetDir}`);
    
    // 检查源目录是否存在
    if (!fs.existsSync(sourceDir)) {
      console.error('❌ Source directory does not exist. Please run "npm run build" first.');
      process.exit(1);
    }
    
    const fileCount = getFileCount(sourceDir);
    console.log(`📊 Found ${fileCount} files to copy`);
    
    // 清空目标目录
    if (fs.existsSync(targetDir)) {
      console.log('🧹 Cleaning target directory...');
      fs.rmSync(targetDir, { recursive: true, force: true });
    }
    
    // 创建目标目录
    fs.mkdirSync(targetDir, { recursive: true });
    
    // 复制所有文件
    console.log('📁 Copying files...');
    const startTime = Date.now();
    copyFolderSync(sourceDir, targetDir);
    const duration = Date.now() - startTime;
    
    console.log(`✅ Successfully copied ${fileCount} files to templates directory`);
    console.log(`⏱️  Copy completed in ${duration}ms`);
    console.log('🎉 Build files are ready for deployment!');
    
    // 验证复制结果
    if (fs.existsSync(targetDir)) {
      const copiedFileCount = getFileCount(targetDir);
      if (copiedFileCount === fileCount) {
        console.log('✅ Verification successful: All files copied correctly');
      } else {
        console.warn(`⚠️  Warning: Expected ${fileCount} files, but found ${copiedFileCount}`);
      }
    }
    
  } catch (error) {
    console.error('❌ Error during copy process:', error.message);
    console.error(error.stack);
    process.exit(1);
  }
}

main();