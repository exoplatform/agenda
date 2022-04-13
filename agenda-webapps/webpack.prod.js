const path = require('path');
const ESLintPlugin = require('eslint-webpack-plugin');
const { VueLoaderPlugin } = require('vue-loader')

const config = {
  context: path.resolve(__dirname, '.'),
  mode: 'production',
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: [
          'babel-loader',
        ]
      },
      {
        test: /\.vue$/,
        use: [
          'vue-loader',
        ]
      }
    ]
  },
  entry: {
    agenda: './src/main/webapp/vue-app/agenda/main.js',
    agendaCommon: './src/main/webapp/vue-app/agenda-common/main.js',
    agendaSettings: './src/main/webapp/vue-app/agenda-user-setting/main.js',
    agendaTimeline: './src/main/webapp/vue-app/agenda-timeline/main.js',
    agendaSearchCard: './src/main/webapp/vue-app/agenda-search/main.js',
    agendaConnectors: './src/main/webapp/vue-app/agenda-connectors/main.js',
    agendaAdminSettings: './src/main/webapp/vue-app/agenda-admin-settings/main.js',
  },
  output: {
    path: path.join(__dirname, 'target/agenda/'),
    filename: 'js/[name].bundle.js',
    libraryTarget: 'amd'
  },
  plugins: [
    new ESLintPlugin({
      files: [
        './src/main/webapp/vue-app/*.js',
        './src/main/webapp/vue-app/*.vue',
        './src/main/webapp/vue-app/**/*.js',
        './src/main/webapp/vue-app/**/*.vue',
      ],
    }),
    new VueLoaderPlugin()
  ],
  externals: {
    vue: 'Vue',
    vuetify: 'Vuetify',
    jquery: '$',
  },
};

module.exports = config;
