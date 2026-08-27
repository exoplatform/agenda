/*
 * Babel configuration scoped to the test environment only.
 *
 * The webpack build runs babel-loader without any configuration file, which
 * makes it an identity transform, and it must keep doing so: adding presets
 * unconditionally here would change the shipped bundles. Jest sets
 * NODE_ENV/BABEL_ENV to "test", so the presets below apply to the test run
 * and to nothing else — every other environment resolves to an empty
 * configuration, exactly as before this file existed.
 */
module.exports = {
  env: {
    test: {
      presets: [
        ['@babel/preset-env', {targets: {node: 'current'}}],
      ],
    },
  },
};
