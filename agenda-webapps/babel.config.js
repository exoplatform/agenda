/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Babel, for the test run only (EXO-90252, the same file caldav-webapp ships).
 *
 * Why this file exists: jest compiles a single-file component through
 * @vue/vue2-jest, which hands the extracted <script> block to Babel and looks
 * for a Babel config FILE to do it with; it cannot see the inline preset of the
 * jest "transform" entry. Without it, a .vue test fails on its first `import`.
 *
 * Why it is guarded by the environment: `babel-loader` in webpack.prod.js runs
 * with no options, so it transpiles nothing today, and an unconditional config
 * file here would silently start transpiling the SHIPPED bundle as a side effect
 * of adding a test. Jest sets NODE_ENV=test; the maven build does not.
 *
 * @param {Object} api the Babel config API
 * @returns {Object} the configuration for the current environment
 */
module.exports = api => {
  api.cache.using(() => process.env.NODE_ENV);
  return {
    presets: api.env('test') ? [['@babel/preset-env', {targets: {node: 'current'}}]] : [],
  };
};
