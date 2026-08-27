/*
 * Globals the portal injects at runtime and that the components read
 * directly. Declared once for the whole test run so a spec never has to
 * rebuild them.
 */
global.eXo = {
  env: {
    portal: {
      context: '/portal',
      portalName: 'dw',
      metaPortalName: 'dw',
      rest: 'rest',
      userName: 'jsmith',
    },
  },
};

/*
 * The portal ships jQuery globally; components use it for one-off DOM
 * wiring in mounted(). A no-op chainable stand-in is enough here.
 */
global.$ = () => ({
  parent: () => ({click: () => {}}),
});
