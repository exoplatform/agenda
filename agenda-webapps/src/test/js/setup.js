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

/*
 * The AMD loader the portal ships. Some service modules probe it at import
 * time — EventWebConferencingService asks whether SHARED/webConferencing is
 * defined as its module body runs — so anything importing them transitively
 * needs it to exist before the import, not merely before a test runs.
 * Answering "nothing is defined" is the truthful stand-in here.
 */
global.require = Object.assign(() => {}, {defined: () => false});
window.require = global.require;
