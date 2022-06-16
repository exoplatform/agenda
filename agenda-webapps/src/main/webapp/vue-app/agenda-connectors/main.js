import googleConnector from './agendaGoogleConnector.js';
import officeConnector from './agendaOfficeConnector.js';
import exchangeConnector from './agendaExchangeConnector.js';

extensionRegistry.registerExtension('agenda', 'connectors', googleConnector);
extensionRegistry.registerExtension('agenda', 'connectors', officeConnector);
extensionRegistry.registerExtension('agenda', 'connectors', exchangeConnector);

document.dispatchEvent(new CustomEvent('agenda-connectors-refresh'));
