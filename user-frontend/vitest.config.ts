import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default defineConfig((configEnv) =>
  mergeConfig(
    viteConfig(configEnv),
    defineConfig({
      test: {
        environment: 'jsdom',
        exclude: [...configDefaults.exclude, 'e2e/**'],
        root: fileURLToPath(new URL('./', import.meta.url)),
        // 让 Vite 处理组件库的 CommonJS 依赖互操作，与浏览器构建保持一致。
        server: { deps: { inline: ['element-plus'] } },
      },
    }),
  ),
)
