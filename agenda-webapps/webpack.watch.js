const path = require('path');
const { merge } = require('webpack-merge');

const webpackProductionConfig = require('./webpack.prod.js');

const app = 'agenda';

const exoServerPath = "/exo-server";

let config = merge(webpackProductionConfig, {
  output: {
    path: path.resolve(`${exoServerPath}/webapps/${app}/`)
  },
  mode: 'development',
  devtool: 'eval-source-map'
});
module.exports = config;