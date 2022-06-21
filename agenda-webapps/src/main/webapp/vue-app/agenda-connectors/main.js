import googleConnector from './agendaGoogleConnector.js';
import officeConnector from './agendaOfficeConnector.js';

extensionRegistry.registerExtension('agenda', 'connectors', googleConnector);
extensionRegistry.registerExtension('agenda', 'connectors', officeConnector);

document.dispatchEvent(new CustomEvent('agenda-connectors-refresh'));
