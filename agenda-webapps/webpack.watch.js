const path = require('path');
const { merge } = require('webpack-merge');

const webpackProductionConfig = require('./webpack.prod.js');

module.exports = merge(webpackProductionConfig, {
  mode: 'development',
  output: {
    path: '/exo-server/webapps/agenda/',
    filename: 'js/[name].bundle.js'
  }
});
