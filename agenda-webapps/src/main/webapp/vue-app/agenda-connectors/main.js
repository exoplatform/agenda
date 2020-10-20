import googleConnector from './agendaGoogleConnector.js';

extensionRegistry.registerExtension('agenda', 'connectors', googleConnector);

document.dispatchEvent(new CustomEvent('agenda-accounts-connectors-refresh'));
