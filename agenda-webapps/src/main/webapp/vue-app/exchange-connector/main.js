import exchangeConnector from './agendaExchangeConnector.js';

extensionRegistry.registerExtension('agenda', 'connectors', exchangeConnector);

document.dispatchEvent(new CustomEvent('agenda-connectors-refresh'));
