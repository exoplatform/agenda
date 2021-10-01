// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

const path = require('path');

const config = {
  context: path.resolve(__dirname, '.'),
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: [
          'babel-loader',
          'eslint-loader',
        ]
      },
      {
        test: /\.vue$/,
        use: [
          'vue-loader',
          'eslint-loader',
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
  externals: {
    vue: 'Vue',
    vuetify: 'Vuetify',
    jquery: '$',
  },
};

module.exports = config;
