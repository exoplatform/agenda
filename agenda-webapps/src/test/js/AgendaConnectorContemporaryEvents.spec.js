import {shallowMount} from '@vue/test-utils';

import * as agendaUtils from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';
import AgendaConnectorContemporaryEvents
  from '../../main/webapp/vue-app/agenda-common/components/remote-event/AgendaConnectorContemporaryEvents.vue';

/*
 * The row is stubbed keeping the props it receives readable, so a spec can
 * pin WHICH events the panel listed and what each row says on hover — the
 * two things EXO-89839 is about.
 */
const RemoteEventItemStub = {
  name: 'agenda-connector-remote-event-item',
  props: ['remoteEvent', 'connector', 'event', 'hoverTitle', 'isEventsList'],
  template: '<div class="remote-event-item-stub"></div>',
};

const passthrough = tag => ({
  template: `<${tag}><slot></slot></${tag}>`,
});

/*
 * $t rendered as the key followed by the values it was given: "named the
 * account" and "named nothing" are otherwise the same string.
 */
function translate(key, params) {
  if (!params) {
    return key;
  }
  return `${key}|${Object.keys(params).sort().map(index => params[index]).join('|')}`;
}

/**
 * A connected, signed-in account whose read resolves with the given events.
 */
function account(name, user, events, options) {
  const opts = options || {};
  return {
    name,
    user,
    connected: true,
    isSignedIn: opts.isSignedIn !== false,
    loading: !!opts.loading,
    rank: opts.rank,
    getEvents: opts.getEvents || (() => Promise.resolve(events || [])),
  };
}

/**
 * An account whose read rejects — the account that could not be reached.
 */
function unreachableAccount(name, user) {
  return account(name, user, null, {
    getEvents: () => Promise.reject(new Error('ECONNREFUSED')),
  });
}

function remoteEvent(id, summary, start, calendarId) {
  return {
    id,
    summary,
    start,
    end: start,
    calendarId,
  };
}

/**
 * An event of the viewer's own eXo calendars, as /v1/agenda/events returns
 * it. `remoteId` is what the endpoint fills for an event imported from — or
 * pushed to — a connected account, and is the identity the deduplication
 * against the live read rides on.
 */
function exoEvent(id, summary, start, options) {
  const opts = options || {};
  return {
    id,
    summary,
    start,
    end: start,
    allDay: !!opts.allDay,
    remoteId: opts.remoteId || null,
    parent: opts.parent || null,
    calendar: opts.calendar || null,
  };
}

function mountPanel(connectors, options) {
  const opts = options || {};
  return shallowMount(AgendaConnectorContemporaryEvents, {
    propsData: {
      connectors,
      event: opts.event || {
        id: 100,
        summary: 'This event',
        startDate: new Date('2026-09-01T10:00:00'),
        endDate: new Date('2026-09-01T11:00:00'),
        attendees: [],
      },
    },
    mocks: {
      $t: translate,
      $agendaUtils: agendaUtils,
      $eventService: {
        getEvents: opts.getExoEvents || (() => Promise.resolve({events: opts.exoEvents || []})),
      },
      $remoteEventConnector: {
        remoteCalendarName: opts.remoteCalendarName || (() => Promise.resolve(null)),
      },
      $root: {$emit: () => {}},
    },
    stubs: {
      'agenda-connector-remote-event-item': RemoteEventItemStub,
      'v-spacer': true,
      'v-progress-linear': true,
      'v-chip': passthrough('div'),
      'v-btn': passthrough('button'),
      'v-icon': passthrough('i'),
    },
  });
}

/**
 * Lets every pending promise chain settle, then re-renders.
 */
async function settle(wrapper) {
  for (let round = 0; round < 5; round++) {
    await Promise.resolve();
    await wrapper.vm.$nextTick();
  }
}

const rows = wrapper => wrapper.findAllComponents(RemoteEventItemStub).wrappers;
/*
 * Every row as "<id>=<hover title>", so a missing row fails on the assertion
 * that wanted it rather than on reading a property of nothing.
 */
const rowTitles = wrapper => rows(wrapper)
  .map(row => `${row.props('remoteEvent').id}=${row.props('hoverTitle')}`);
const emptyStateText = wrapper => wrapper.find('.contemporary-events-empty').find('span').text();

describe('AgendaConnectorContemporaryEvents heading and sources', () => {

  it('titles the panel by its purpose and names no account in the heading', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
      account('google', 'alice@gmail.com', []),
    ]);
    await settle(wrapper);

    const heading = wrapper.find('.text-title-color');
    expect(heading.text()).toBe('agenda.contemporaryEvents.title');
    expect(heading.text()).not.toContain('anais@demo.fr');
    expect(heading.text()).not.toContain('alice@gmail.com');
    wrapper.destroy();
  });

  /*
   * The pin EXO-89899 exists for. The roster named every connected account
   * above the events; the heading already says whose calendars these are and
   * every row names the calendar it lives in, so the list said nothing twice
   * over. $t renders a key as itself in this spec, so a connector's name is
   * also its rendered label — a name reaching the panel would show here.
   */
  it('names no connected account above the events', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
      account('google', 'alice@gmail.com', []),
    ]);
    await settle(wrapper);

    expect(wrapper.text()).not.toContain('caldav');
    expect(wrapper.text()).not.toContain('google');
    wrapper.destroy();
  });

  /*
   * The consequence EXO-89899 accepted, pinned rather than softened: a
   * disconnected or unreachable account is no longer reported on this panel.
   * Its events are simply missing and nothing here says which account owed
   * them. The only trace left is the empty-state sentence, which stops short
   * of claiming the list is complete — pinned in its own describe below.
   *
   * Read as a decision, not a defect: reinstating the report in any form is a
   * change to that decision, not a fix to this spec.
   */
  it('says nothing about an account that is disconnected or could not be reached', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
      account('google', 'alice@gmail.com', [], {isSignedIn: false}),
      unreachableAccount('other', 'bob@contoso.com'),
    ]);
    await settle(wrapper);

    expect(wrapper.text()).not.toContain('agenda.contemporaryEvents.accountSignedOut');
    expect(wrapper.text()).not.toContain('agenda.contemporaryEvents.accountUnreachable');
    expect(wrapper.text()).not.toContain('google');
    expect(wrapper.text()).not.toContain('other');
    wrapper.destroy();
  });

  /*
   * The pin EXO-89896 exists for, kept because the panel can still render text
   * an account gave it: a row's hover title carries the collection name the
   * account resolved. The assertion is panel-wide on purpose — it does not
   * care where an address would appear, only that none does.
   */
  it('shows no account address anywhere, in any account state', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('dentist', 'Dentist', '2026-09-01T14:00:00', '/dav/cal/anais/personal'),
      ]),
      account('google', 'alice@gmail.com', [], {isSignedIn: false}),
      unreachableAccount('other', 'bob@contoso.com'),
    ], {
      remoteCalendarName: () => Promise.resolve(null),
    });
    await settle(wrapper);

    const rendered = `${wrapper.html()} ${rowTitles(wrapper).join(' ')}`;
    expect(rendered).not.toContain('anais@demo.fr');
    expect(rendered).not.toContain('alice@gmail.com');
    expect(rendered).not.toContain('bob@contoso.com');
    wrapper.destroy();
  });

  /*
   * The panel used to mount a connectors drawer of its own, for the roster to
   * click through to. It only ever renders inside the event dialog, and both
   * hosts of that dialog — Agenda.vue and AgendaTimelineWidget.vue — already
   * mount one beside it; since every mounted drawer subscribes to the same
   * $root event in created(), that second instance opened stacked on the
   * host's. EXO-89899 removed it with the roster that needed it.
   */
  it('mounts no connectors drawer of its own', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
    ]);
    await settle(wrapper);

    expect(wrapper.html()).not.toContain('agenda-connectors-drawer');
    expect(wrapper.html().toLowerCase()).not.toContain('agendaconnectorsdrawer');
    wrapper.destroy();
  });

  /*
   * EXO-89896 removed the offer to connect from this panel: it is mid-task on
   * a surface whose job is content, and the content is there whether or not an
   * account is connected. The two things it removed are pinned by what a user
   * would see — the plug icon and the synchronisation pitch.
   */
  it('never offers to connect an account, however few are connected', async () => {
    const wrapper = mountPanel([
      {name: 'caldav', enabled: true, connected: false},
    ]);
    await settle(wrapper);

    expect(wrapper.html()).not.toContain('fa-plug');
    expect(wrapper.text()).not.toContain('agenda.connectYourPersonalAgenda');
    expect(wrapper.text()).not.toContain('agenda.synchronizeEventsWithPersonalCalendarSubTitle');
    wrapper.destroy();
  });
});

describe('AgendaConnectorContemporaryEvents when an account could not be reached', () => {

  /*
   * What survives EXO-89899 of the EXO-89839 invariant. The panel no longer
   * names the account that failed, so the one thing it still owes the reader
   * is that it must not claim the list is complete: an empty list after a
   * failed read means "nothing on the sources that could be checked", never
   * "nothing".
   */
  it('never calls the list complete when an account could not be reached', async () => {
    const wrapper = mountPanel([
      unreachableAccount('caldav', 'bob@contoso.com'),
    ]);
    await settle(wrapper);

    expect(rows(wrapper)).toHaveLength(0);
    expect(emptyStateText(wrapper)).toBe('agenda.contemporaryEvents.noEventsPartial');
    expect(wrapper.text()).not.toContain('agenda.noRemoteEvents');
    wrapper.destroy();
  });

  /*
   * The same for an account whose session expired rather than one whose read
   * failed: the two used to render as different roster lines and now differ
   * only here, so both paths into hasUnansweredSource need their own pin.
   */
  it('never calls the list complete when an account is disconnected', async () => {
    const wrapper = mountPanel([
      account('google', 'alice@gmail.com', [], {isSignedIn: false}),
    ]);
    await settle(wrapper);

    expect(rows(wrapper)).toHaveLength(0);
    expect(emptyStateText(wrapper)).toBe('agenda.contemporaryEvents.noEventsPartial');
    wrapper.destroy();
  });

  it('keeps the events the reachable accounts returned', async () => {
    const wrapper = mountPanel([
      unreachableAccount('caldav', 'bob@contoso.com'),
      account('google', 'alice@gmail.com', [
        remoteEvent('standup', 'Weekly standup', '2026-09-01T09:00:00'),
      ]),
    ]);
    await settle(wrapper);

    const summaries = rows(wrapper).map(row => row.props('remoteEvent').summary);
    expect(summaries).toContain('Weekly standup');
    wrapper.destroy();
  });

  it('says the list is complete only when every account answered', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
      account('google', 'alice@gmail.com', []),
    ]);
    await settle(wrapper);

    expect(emptyStateText(wrapper)).toBe('agenda.contemporaryEvents.noEvents');
    wrapper.destroy();
  });
});

describe('AgendaConnectorContemporaryEvents row provenance', () => {

  it('says which collection a row was read from', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('dentist', 'Dentist', '2026-09-01T14:00:00', '/dav/cal/anais/personal'),
      ]),
    ], {
      remoteCalendarName: () => Promise.resolve('Personal'),
    });
    await settle(wrapper);

    expect(rowTitles(wrapper))
      .toContain('dentist=agenda.contemporaryEvents.rowCalendar|Dentist|Personal');
    wrapper.destroy();
  });

  /*
   * EXO-89896: when the collection cannot be named, the row says nothing
   * about where it lives rather than something that is not a calendar name —
   * neither the address the account is signed in as, nor the collection href,
   * nor the connector name. The rule the row already applied to an eXo
   * calendar with no title of its own.
   */
  it('says nothing about the collection, rather than an address or an href, when it cannot be named', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('dentist', 'Dentist', '2026-09-01T14:00:00', '/dav/cal/anais/personal'),
      ]),
    ], {
      remoteCalendarName: () => Promise.resolve(null),
    });
    await settle(wrapper);

    expect(rowTitles(wrapper)).toContain('dentist=Dentist');
    const titles = rowTitles(wrapper).join(' ');
    expect(titles).not.toContain('anais@demo.fr');
    expect(titles).not.toContain('/dav/cal/anais/personal');
    expect(titles).not.toContain('caldav');
    wrapper.destroy();
  });

  it('adds nothing to a row that carries no collection', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('standup', 'Weekly standup', '2026-09-01T09:00:00'),
      ]),
    ]);
    await settle(wrapper);

    expect(rowTitles(wrapper)).toContain('standup=Weekly standup');
    wrapper.destroy();
  });
});

describe('AgendaConnectorContemporaryEvents reads', () => {

  it('leaves the event it was given untouched while widening the read window', async () => {
    const event = {
      id: 100,
      summary: 'This event',
      startDate: new Date('2026-09-01T10:00:00'),
      endDate: new Date('2026-09-01T11:00:00'),
      attendees: [],
    };
    const wrapper = mountPanel([account('caldav', 'anais@demo.fr', [])], {event});
    await settle(wrapper);

    expect(event.startDate.getHours()).toBe(10);
    expect(event.startDate.getMinutes()).toBe(0);
    expect(event.endDate.getHours()).toBe(11);
    expect(event.endDate.getMinutes()).toBe(0);
    wrapper.destroy();
  });

  it('drops a read that lands after the screen moved to another event', async () => {
    const pendingReads = [];
    const slow = account('caldav', 'anais@demo.fr', null, {
      getEvents: () => new Promise(resolve => pendingReads.push(resolve)),
    });
    const wrapper = mountPanel([slow]);
    await settle(wrapper);
    expect(pendingReads).toHaveLength(1);

    // The dialog moves to another event, which starts a second read.
    wrapper.setProps({
      event: {
        id: 200,
        summary: 'Another event',
        startDate: new Date('2026-09-02T10:00:00'),
        endDate: new Date('2026-09-02T11:00:00'),
        attendees: [],
      },
    });
    await settle(wrapper);
    expect(pendingReads).toHaveLength(2);

    pendingReads[1]([remoteEvent('current', 'What is on this event\'s day', '2026-09-02T09:00:00')]);
    await settle(wrapper);

    // ...and only then does the first read answer, about the previous day.
    pendingReads[0]([remoteEvent('stale', 'A stale answer', '2026-09-01T08:00:00')]);
    await settle(wrapper);

    const summaries = rows(wrapper).map(row => row.props('remoteEvent').summary);
    expect(summaries).toContain('What is on this event\'s day');
    expect(summaries).not.toContain('A stale answer');
    wrapper.destroy();
  });

  it('keys a row by a string, so two rows are told apart', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('standup', 'Weekly standup', '2026-09-01T09:00:00'),
      ]),
    ]);
    await settle(wrapper);

    expect(wrapper.vm.remoteEvents).toHaveLength(1);
    const key = wrapper.vm.remoteEventKey(wrapper.vm.remoteEvents[0]);
    expect(typeof key).toBe('string');
    expect(key).toContain('caldav');
    expect(key).toContain('standup');
    wrapper.destroy();
  });
});

describe('AgendaConnectorContemporaryEvents reads the viewer\'s own calendars', () => {

  /*
   * The pin EXO-89840 exists for. Both rig accounts have their collections
   * materialised, so the live read returns nothing by construction: an event
   * sitting in a materialised eXo calendar has to reach the list all the
   * same, or the panel says "nothing" about a day that is full.
   */
  it('lists an event of a materialised calendar even when the live read is empty by construction', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
    ], {
      exoEvents: [
        exoEvent(501, 'Materialised standup', '2026-09-01T09:00:00', {remoteId: 'uid-standup'}),
      ],
    });
    await settle(wrapper);

    const summaries = rows(wrapper).map(row => row.props('remoteEvent').summary);
    expect(summaries).toContain('Materialised standup');
    expect(wrapper.find('.contemporary-events-empty').exists()).toBe(false);
    wrapper.destroy();
  });

  it('says which eXo calendar a row is in', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
    ], {
      exoEvents: [
        exoEvent(501, 'Materialised standup', '2026-09-01T09:00:00', {
          remoteId: 'uid-standup',
          calendar: {id: 187, title: 'Work'},
        }),
      ],
    });
    await settle(wrapper);

    expect(rowTitles(wrapper))
      .toContain('501=agenda.contemporaryEvents.rowExoCalendar|Materialised standup|Work');
    wrapper.destroy();
  });

  it('does not repeat the event the panel is describing', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
    ], {
      exoEvents: [
        exoEvent(100, 'This event', '2026-09-01T10:00:00'),
        exoEvent(501, 'Materialised standup', '2026-09-01T09:00:00'),
      ],
    });
    await settle(wrapper);

    const ids = rows(wrapper).map(row => row.props('remoteEvent').id);
    expect(ids.filter(id => id === 100)).toHaveLength(1);
    wrapper.destroy();
  });

  /*
   * An occurrence of a recurring series is sent with an id of zero and the
   * series as its parent, so an identity read off the id alone would not
   * recognise the occurrence the panel is describing — and the panel would
   * list the very event it is about, beside itself.
   */
  it('does not repeat the occurrence the panel is describing', async () => {
    const occurrence = {
      id: 0,
      parent: {id: 700},
      summary: 'Daily standup',
      startDate: new Date('2026-09-01T10:00:00'),
      endDate: new Date('2026-09-01T11:00:00'),
      attendees: [],
    };
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
    ], {
      event: occurrence,
      exoEvents: [
        exoEvent(0, 'Daily standup', '2026-09-01T10:00:00', {parent: {id: 700}}),
        exoEvent(0, 'Daily standup', '2026-09-01T16:00:00', {parent: {id: 700}}),
      ],
    });
    await settle(wrapper);

    const starts = rows(wrapper).map(row => wrapper.vm.startInstant(row.props('remoteEvent')));
    expect(starts).toHaveLength(2);
    expect(new Set(starts).size).toBe(2);
    wrapper.destroy();
  });
});

describe('AgendaConnectorContemporaryEvents deduplication across the two sources', () => {

  /*
   * The meeting is in a materialised eXo calendar AND still returned by the
   * live read — a collection eXo imported that the connector kept reporting,
   * or an eXo event pushed to an account. Showing it twice is worse than the
   * emptiness being fixed, so the identity has to span the two sources: the
   * remote object's own identifier, which eXo records as `remoteId` and the
   * live read returns as `id`, together with the occurrence's start.
   */
  it('shows an event reachable from both sources exactly once', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('uid-standup', 'Weekly standup', '2026-09-01T09:00:00', '/dav/cal/anais/work'),
      ]),
    ], {
      exoEvents: [
        exoEvent(501, 'Weekly standup', '2026-09-01T09:00:00', {remoteId: 'uid-standup'}),
      ],
    });
    await settle(wrapper);

    const standups = rows(wrapper).filter(row => row.props('remoteEvent').summary === 'Weekly standup');
    expect(standups).toHaveLength(1);
    // The eXo copy is the one kept: it is the copy the viewer can act on.
    expect(standups[0].props('remoteEvent').id).toBe(501);
    wrapper.destroy();
  });

  /*
   * An occurrence of a recurring series carries no remote mapping of its own
   * — the endpoint records it on the series — so its identity has to be read
   * off the parent, or the same occurrence shows up once per source.
   */
  it('recognises an occurrence through its series identifier', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('uid-series', 'Daily standup', '2026-09-01T09:00:00', '/dav/cal/anais/work'),
      ]),
    ], {
      exoEvents: [
        exoEvent(0, 'Daily standup', '2026-09-01T09:00:00', {
          parent: {id: 501, remoteId: 'uid-series'},
        }),
      ],
    });
    await settle(wrapper);

    const standups = rows(wrapper).filter(row => row.props('remoteEvent').summary === 'Daily standup');
    expect(standups).toHaveLength(1);
    wrapper.destroy();
  });

  /*
   * ...and the start is what keeps the siblings apart: every occurrence of
   * the series shares that identifier, so an identity without the start
   * would collapse a day holding two of them into one row.
   */
  it('keeps two occurrences of one series apart, sharing an identifier as they do', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('uid-series', 'Daily standup', '2026-09-01T15:00:00', '/dav/cal/anais/work'),
      ]),
    ], {
      exoEvents: [
        exoEvent(0, 'Daily standup', '2026-09-01T09:00:00', {
          parent: {id: 501, remoteId: 'uid-series'},
        }),
      ],
    });
    await settle(wrapper);

    const standups = rows(wrapper).filter(row => row.props('remoteEvent').summary === 'Daily standup');
    expect(standups).toHaveLength(2);
    wrapper.destroy();
  });

  it('keeps an event the live read alone holds', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('uid-dentist', 'Dentist', '2026-09-01T14:00:00', '/dav/cal/anais/never-imported'),
      ]),
    ], {
      exoEvents: [
        exoEvent(501, 'Materialised standup', '2026-09-01T09:00:00', {remoteId: 'uid-standup'}),
      ],
    });
    await settle(wrapper);

    const summaries = rows(wrapper).map(row => row.props('remoteEvent').summary);
    expect(summaries).toContain('Dentist');
    expect(summaries).toContain('Materialised standup');
    wrapper.destroy();
  });

  it('interleaves the two sources chronologically rather than grouping them', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('uid-early', 'Live at 08:00', '2026-09-01T08:00:00', '/dav/cal/anais/never-imported'),
        remoteEvent('uid-late', 'Live at 16:00', '2026-09-01T16:00:00', '/dav/cal/anais/never-imported'),
      ]),
    ], {
      exoEvents: [
        exoEvent(501, 'eXo at 09:00', '2026-09-01T09:00:00'),
      ],
    });
    await settle(wrapper);

    expect(rows(wrapper).map(row => row.props('remoteEvent').summary)).toEqual([
      'Live at 08:00',
      'eXo at 09:00',
      'This event',
      'Live at 16:00',
    ]);
    wrapper.destroy();
  });
});

describe('AgendaConnectorContemporaryEvents when the eXo read fails', () => {

  /*
   * What survives EXO-89899 of the EXO-89839 invariant on the eXo source. The
   * failure is no longer named — the line that named it went with the roster —
   * so the teeth are in the sentence: a read that failed must not leave the
   * panel calling the list complete.
   */
  it('never calls the list complete when the eXo read failed', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
    ], {
      getExoEvents: () => Promise.reject(new Error('HTTP 500')),
    });
    await settle(wrapper);

    expect(rows(wrapper)).toHaveLength(0);
    expect(emptyStateText(wrapper)).toBe('agenda.contemporaryEvents.noEventsPartial');
    wrapper.destroy();
  });

  it('keeps the events the accounts returned when the eXo read failed', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('uid-dentist', 'Dentist', '2026-09-01T14:00:00', '/dav/cal/anais/never-imported'),
      ]),
    ], {
      getExoEvents: () => Promise.reject(new Error('HTTP 500')),
    });
    await settle(wrapper);

    expect(rows(wrapper).map(row => row.props('remoteEvent').summary)).toContain('Dentist');
    wrapper.destroy();
  });

  /*
   * The full sentence claims every source was consulted and every one was
   * empty. It may only be said when that is true of BOTH sources.
   */
  it('says the list is complete only when both sources answered', async () => {
    const answered = mountPanel([account('caldav', 'anais@demo.fr', [])]);
    await settle(answered);
    expect(emptyStateText(answered)).toBe('agenda.contemporaryEvents.noEvents');
    answered.destroy();

    const exoFailed = mountPanel([account('caldav', 'anais@demo.fr', [])], {
      getExoEvents: () => Promise.reject(new Error('HTTP 500')),
    });
    await settle(exoFailed);
    expect(emptyStateText(exoFailed)).toBe('agenda.contemporaryEvents.noEventsPartial');
    exoFailed.destroy();
  });

  /*
   * The other half of what EXO-89899 removed, pinned rather than softened: a
   * failed eXo read is not named on this panel either, no more than a failed
   * account is. Benjamin chose the whole block gone over keeping a line for
   * the failing case — reinstating it in any form is a change to that
   * decision, not a fix to this spec.
   */
  it('says nothing about eXo\'s calendars, whether or not they could be read', async () => {
    const answered = mountPanel([account('caldav', 'anais@demo.fr', [])]);
    await settle(answered);
    expect(answered.text()).not.toContain('agenda.contemporaryEvents.exoCalendarsUnreachable');
    answered.destroy();

    const failed = mountPanel([account('caldav', 'anais@demo.fr', [])], {
      getExoEvents: () => Promise.reject(new Error('HTTP 500')),
    });
    await settle(failed);
    expect(failed.text()).not.toContain('agenda.contemporaryEvents.exoCalendarsUnreachable');
    // and nothing else stands between the heading and the list either
    expect(failed.find('.contemporary-events-exo-unreachable').exists()).toBe(false);
    expect(failed.find('.contemporary-events-account').exists()).toBe(false);
    failed.destroy();
  });
});

describe('AgendaConnectorContemporaryEvents with no account connected', () => {

  /*
   * eXo's own calendars answer whether or not an account is connected, so
   * hiding what they hold behind a connector would be the same emptiness in
   * another guise.
   */
  it('still lists what the viewer\'s own calendars hold', async () => {
    const wrapper = mountPanel([
      {name: 'caldav', enabled: true, connected: false},
    ], {
      exoEvents: [
        exoEvent(501, 'A meeting in eXo', '2026-09-01T09:00:00'),
      ],
    });
    await settle(wrapper);

    expect(rows(wrapper).map(row => row.props('remoteEvent').summary)).toContain('A meeting in eXo');
    wrapper.destroy();
  });
});
