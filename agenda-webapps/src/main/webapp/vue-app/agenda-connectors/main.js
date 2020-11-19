import googleConnector from './agendaGoogleConnector.js';
import './initComponents.js';

extensionRegistry.registerExtension('agenda', 'connectors', googleConnector);

document.dispatchEvent(new CustomEvent('agenda-accounts-connectors-refresh'));
