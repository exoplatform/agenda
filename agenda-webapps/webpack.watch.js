const path = require('path');
const { merge } = require('webpack-merge');

const webpackProductionConfig = require('./webpack.prod.js');

module.exports = merge(webpackProductionConfig, {
  mode: 'development',
  output: {
    path: 'D:\\\eXo\\servers\\platform-7.2.0-M03/webapps/agenda/',
    filename: 'js/[name].bundle.js'
  }
});
