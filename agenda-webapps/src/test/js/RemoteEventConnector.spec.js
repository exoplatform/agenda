import {caldavManagedServerName, isCaldavManaged, remoteCalendarName} from '../../main/webapp/vue-app/agenda-common/js/RemoteEventConnector.js';

/*
 * EXO-89825. An event read live from a connected account carries the href of
 * the collection it lives in and nothing else about where it lives, so the
 * only thing that can name it is the account's own calendar listing.
 */
describe('remoteCalendarName', () => {

  /**
   * A connector able to list calendars, counting how often it is asked.
   *
   * @param {Array} calendars what the account answers with
   * @returns {Object} the connector, carrying a `calls` counter
   */
  function listingConnector(calendars) {
    const connector = {
      name: 'caldav',
      canListCalendars: true,
      calls: 0,
      listCalendars() {
        connector.calls++;
        return Promise.resolve(calendars);
      },
    };
    return connector;
  }

  it('names the collection whose href the event carries', async () => {
    const connector = listingConnector([
      {id: '/dav/cal/alice@stalwart.local/work', name: 'FRANCOIS'},
      {id: '/dav/cal/alice@stalwart.local/default', name: 'MYCAL2'},
    ]);
    await expect(remoteCalendarName(connector, '/dav/cal/alice@stalwart.local/default'))
      .resolves.toBe('MYCAL2');
  });

  it('matches on the href and never on position in the listing', async () => {
    // The wanted collection is last, so returning the listing's first or
    // second entry — the shape of a position-based match — cannot pass.
    const connector = listingConnector([
      {id: '/dav/cal/alice@stalwart.local/work', name: 'FRANCOIS'},
      {id: '/dav/cal/alice@stalwart.local/default', name: 'MYCAL2'},
      {id: '/dav/cal/alice@stalwart.local/holidays', name: 'Holidays'},
    ]);
    await expect(remoteCalendarName(connector, '/dav/cal/alice@stalwart.local/holidays'))
      .resolves.toBe('Holidays');
  });

  it('matches through percent-encoding, so an encoded mailbox still resolves', async () => {
    const connector = listingConnector([
      {id: '/dav/cal/alice%40stalwart.local/default', name: 'MYCAL2'},
    ]);
    await expect(remoteCalendarName(connector, '/dav/cal/alice@stalwart.local/default'))
      .resolves.toBe('MYCAL2');
  });

  it('resolves to null for a collection the connector reads but does not list', async () => {
    // The CalDAV connector omits the collections eXo already materialised and
    // the ones eXo itself created but no longer holds a binding for, while
    // still reading events from the latter — the copy written by another eXo
    // deployment that started EXO-89825.
    const connector = listingConnector([
      {id: '/dav/cal/alice@stalwart.local/default', name: 'MYCAL2'},
    ]);
    await expect(remoteCalendarName(connector, '/dav/cal/alice@stalwart.local/exo-meetings'))
      .resolves.toBeNull();
  });

  it('asks the account once however many events are looked up', async () => {
    const connector = listingConnector([
      {id: '/dav/cal/alice@stalwart.local/default', name: 'MYCAL2'},
    ]);
    await Promise.all([
      remoteCalendarName(connector, '/dav/cal/alice@stalwart.local/default'),
      remoteCalendarName(connector, '/dav/cal/alice@stalwart.local/default'),
      remoteCalendarName(connector, '/dav/cal/alice@stalwart.local/other'),
    ]);
    expect(connector.calls).toBe(1);
  });

  it('resolves to null rather than throwing when the account is unreachable', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => {});
    const connector = {
      name: 'caldav',
      canListCalendars: true,
      listCalendars: () => Promise.reject(new Error('unreachable')),
    };
    await expect(remoteCalendarName(connector, '/dav/cal/alice@stalwart.local/default'))
      .resolves.toBeNull();
    console.error.mockRestore();
  });

  it('asks nothing of a connector that cannot list calendars', async () => {
    let asked = 0;
    const connector = {
      name: 'legacy',
      listCalendars: () => {
        asked++;
        return Promise.resolve([]);
      },
    };
    await expect(remoteCalendarName(connector, '/dav/cal/alice@stalwart.local/default'))
      .resolves.toBeNull();
    expect(asked).toBe(0);
  });

  it('asks nothing when the event carries no collection href', async () => {
    const connector = listingConnector([
      {id: '/dav/cal/alice@stalwart.local/default', name: 'MYCAL2'},
    ]);
    await expect(remoteCalendarName(connector, '')).resolves.toBeNull();
    expect(connector.calls).toBe(0);
  });
});

/*
 * EXO-89900. The single condition every screen reads before hiding a CalDAV
 * connect or disconnect affordance. It lives here — beside lastSyncPhrase, and
 * for the same stated reason — because a condition copied into four components
 * is four conditions, and they drift.
 */
describe('isCaldavManaged', () => {

  /**
   * A connector descriptor as the CalDAV add-on stamps them.
   *
   * @param {Object} overrides what to change on the default CalDAV descriptor
   * @returns {Object} the descriptor
   */
  function descriptor(overrides) {
    return Object.assign({
      name: 'agenda.caldavCalendar.6',
      isCaldav: true,
      managed: false,
      managedServerName: null,
    }, overrides);
  }

  it('is true when a CalDAV descriptor carries the managed stamp', () => {
    expect(isCaldavManaged([descriptor({managed: true, managedServerName: 'Bluemind'})])).toBe(true);
  });

  it('is false when no CalDAV descriptor is managed', () => {
    expect(isCaldavManaged([descriptor({})])).toBe(false);
  });

  /*
   * Managed mode governs the CalDAV family only. A Google or Office 365
   * descriptor carrying the flag — by accident, or because another add-on
   * copied the shape — must not take the CalDAV affordances away, and must
   * certainly not take its own.
   */
  it('ignores a non-CalDAV descriptor whatever it carries', () => {
    expect(isCaldavManaged([{name: 'agenda.googleCalendar', isCaldav: false, managed: true}])).toBe(false);
    expect(isCaldavManaged([{name: 'agenda.officeCalendar', managed: true}])).toBe(false);
  });

  /*
   * A descriptor shipped before the stamp existed counts as unmanaged, which
   * is the behaviour every deployment had before managed mode.
   */
  it('reads a descriptor without the stamp as unmanaged', () => {
    expect(isCaldavManaged([{name: 'agenda.caldavCalendar', isCaldav: true}])).toBe(false);
  });

  it('answers false rather than throwing on nothing at all', () => {
    expect(isCaldavManaged(null)).toBe(false);
    expect(isCaldavManaged([])).toBe(false);
    expect(isCaldavManaged([null, undefined])).toBe(false);
  });
});

/*
 * The server's name is a different question from the verdict, kept apart on
 * purpose: the verdict decides whether an affordance is offered and every
 * screen must agree on it, the name is a word one screen prints.
 */
describe('caldavManagedServerName', () => {

  it('names the server the managed descriptor carries', () => {
    expect(caldavManagedServerName([
      {isCaldav: true, managed: true, managedServerName: 'Bluemind'},
    ])).toBe('Bluemind');
  });

  it('names nothing when nothing is managed', () => {
    expect(caldavManagedServerName([{isCaldav: true, managed: false, managedServerName: 'Bluemind'}])).toBe('');
    expect(caldavManagedServerName(null)).toBe('');
  });
});
