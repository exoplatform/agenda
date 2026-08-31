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

const ConnectorAvatarStub = {
  name: 'agenda-connector-avatar',
  props: ['connector'],
  template: '<div class="connector-avatar-stub"></div>',
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
      $remoteEventConnector: {
        remoteCalendarName: opts.remoteCalendarName || (() => Promise.resolve(null)),
      },
      $root: {$emit: () => {}},
    },
    stubs: {
      'agenda-connector-avatar': ConnectorAvatarStub,
      'agenda-connector-remote-event-item': RemoteEventItemStub,
      'agenda-connectors-drawer': true,
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

const rosterLabels = wrapper => wrapper.findAll('.contemporary-events-account')
  .wrappers.map(entry => entry.find('span').text());
const rows = wrapper => wrapper.findAllComponents(RemoteEventItemStub).wrappers;
/*
 * Every row as "<id>=<hover title>", so a missing row fails on the assertion
 * that wanted it rather than on reading a property of nothing.
 */
const rowTitles = wrapper => rows(wrapper)
  .map(row => `${row.props('remoteEvent').id}=${row.props('hoverTitle')}`);
const emptyStateText = wrapper => wrapper.find('.contemporary-events-empty').find('span').text();

describe('AgendaConnectorContemporaryEvents heading and roster', () => {

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

  it('names every connected account in the roster, not only the first', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
      account('google', 'alice@gmail.com', []),
    ]);
    await settle(wrapper);

    expect(rosterLabels(wrapper)).toEqual(['anais@demo.fr', 'alice@gmail.com']);
    wrapper.destroy();
  });

  it('states the disconnection on the account it belongs to, whatever its rank in the list', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', []),
      account('google', 'alice@gmail.com', [], {isSignedIn: false}),
    ]);
    await settle(wrapper);

    expect(rosterLabels(wrapper)).toEqual([
      'anais@demo.fr',
      'agenda.contemporaryEvents.accountSignedOut|alice@gmail.com',
    ]);
    wrapper.destroy();
  });
});

describe('AgendaConnectorContemporaryEvents when an account could not be reached', () => {

  /*
   * The pin that matters: a failed account must not read as an account that
   * answered "nothing". Two things have to hold at once — the panel says the
   * account failed, and it does NOT say the list is complete.
   */
  it('never renders a failed account as an empty one', async () => {
    const wrapper = mountPanel([
      unreachableAccount('caldav', 'bob@contoso.com'),
    ]);
    await settle(wrapper);

    expect(rows(wrapper)).toHaveLength(0);
    expect(rosterLabels(wrapper)).toEqual([
      'agenda.contemporaryEvents.accountUnreachable|bob@contoso.com',
    ]);
    expect(emptyStateText(wrapper)).toBe('agenda.contemporaryEvents.noEventsPartial');
    expect(wrapper.text()).not.toContain('agenda.noRemoteEvents');
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
    expect(rosterLabels(wrapper)).toContain('agenda.contemporaryEvents.accountUnreachable|bob@contoso.com');
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

  it('falls back to the account, never to the connector name, when the collection cannot be named', async () => {
    const wrapper = mountPanel([
      account('caldav', 'anais@demo.fr', [
        remoteEvent('dentist', 'Dentist', '2026-09-01T14:00:00', '/dav/cal/anais/personal'),
      ]),
    ], {
      remoteCalendarName: () => Promise.resolve(null),
    });
    await settle(wrapper);

    expect(rowTitles(wrapper))
      .toContain('dentist=agenda.contemporaryEvents.rowCalendar|Dentist|agenda.remoteEvent.calendarOfAccount|anais@demo.fr');
    expect(rowTitles(wrapper).join(' ')).not.toContain('caldav');
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
