import pluginVue from "eslint-plugin-vue";
import js from "@eslint/js";

export default [
  js.configs.recommended,
  ...pluginVue.configs["flat/recommended"],
  {
    files: ["**/*.{js,vue}"],
    languageOptions: {
      globals: {
        // browser
        localStorage: "readonly",
        sessionStorage: "readonly",
        FormData: "readonly",
        File: "readonly",
        FileReader: "readonly",
        EventSource: "readonly",
        setInterval: "readonly",
        clearInterval: "readonly",
        setTimeout: "readonly",
        clearTimeout: "readonly",
        fetch: "readonly",
        URL: "readonly",
        URLSearchParams: "readonly",
        confirm: "readonly",
        alert: "readonly",
        prompt: "readonly",
        window: "readonly",
        document: "readonly",
        // vitest
        vi: "readonly",
        global: "readonly",
      },
    },
    rules: {
      "vue/multi-word-component-names": "off",
      "vue/max-attributes-per-line": "off",
      "vue/singleline-html-element-content-newline": "off",
      "vue/html-self-closing": "off",
      "vue/html-closing-bracket-spacing": "warn",
      "vue/attributes-order": "warn",
      "no-unused-vars": ["warn", { argsIgnorePattern: "^_" }],
      "no-console": "warn",
      "no-empty": ["error", { allowEmptyCatch: true }],
    },
  },
  {
    files: ["*.config.js", "vite.config.js", "vitest.config.js"],
    languageOptions: {
      globals: {
        __dirname: "readonly",
        __filename: "readonly",
        process: "readonly",
        require: "readonly",
        module: "readonly",
        exports: "readonly",
      },
    },
  },
  {
    ignores: ["dist/**", "node_modules/**", "coverage/**"],
  },
];
