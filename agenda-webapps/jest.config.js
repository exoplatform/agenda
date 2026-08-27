/*
 * Jest harness for the Agenda Vue components. The Babel presets it needs
 * live in babel.config.js, scoped to the "test" environment so the webpack
 * build keeps running babel-loader as an identity transform.
 */
module.exports = {
  rootDir: __dirname,
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/src/test/js'],
  testMatch: ['**/*.spec.js'],
  moduleFileExtensions: ['js', 'json', 'vue'],
  transform: {
    '^.+\\.vue$': '@vue/vue2-jest',
    '^.+\\.js$': 'babel-jest',
  },
  setupFiles: ['<rootDir>/src/test/js/setup.js'],
};
