import {mount} from '@vue/test-utils';
import AgendaEventFormBusyCoverage from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormBusyCoverage.vue';
import * as agendaUtils from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';

/*
 * EXO-89850, the header of the date-poll "Suggest dates" step.
 *
 * Benjamin saw three stacked full-width rows above the grid: a whole row for
 * the connected account's email address, a row for the coverage sentence, and
 * a row for the failure sentence. "UI is not good... use 3 icons in the header
 * and the text as tooltip."
 *
 * What is compacted and what is NOT is the whole of these pins:
 *
 *   - the coverage SENTENCE becomes a tooltip, but the COUNT stays visible
 *     text: a number is scannable, an icon alone is not.
 *   - the failure line stays BODY TEXT. A tooltip does not exist on touch, and
 *     an organiser who never hovers never learns their grid is incomplete —
 *     which is the "a state that looks like an answer" failure this screen was
 *     built to prevent. Compact in the quiet case, loud in the case that can
 *     mislead someone.
 */

/*
 * Mounted without Vuetify, the way every other spec here does it: the platform
 * supplies it at runtime and it is not a devDependency. The v-* elements
 * render as unknown custom elements, which keeps their attributes and their
 * children — enough to assert what is text and what is a tooltip, which is
 * precisely what is under test.
 */

/**
 * An attendee as the suggester hands one over.
 *
 * @param {String} remoteId the username
 * @param {String} fullname how the person is named
 * @returns {Object} the attendee
 */
function invited(remoteId, fullname) {
  return {identity: {providerId: 'organization', remoteId, profile: {fullname}}};
}

const SARA = invited('sara', 'Sara Green');

const TOM = invited('tom', 'Tom Ray');

/**
 * Mounts the coverage component in one of its two variants.
 *
 * @param {String} variant counter or report
 * @param {Object} props the coverage state
 * @returns {Object} the mounted wrapper
 */
function mountCoverage(variant, props) {
  return mount(AgendaEventFormBusyCoverage, {
    propsData: Object.assign({
      variant,
      participants: [],
      checkedKeys: [],
      notDisclosedKeys: [],
      failedKeys: [],
    }, props),
    mocks: {
      $agendaUtils: agendaUtils,
      $t: (key, params) => (params ? `${key}|${Object.keys(params).map(index => params[index]).join('|')}` : key),
    },
  });
}

/*
 * The connected-account plug that used to sit first in this header is gone
 * (EXO-89869): an account is connected from the settings, not in the middle of
 * creating an event, so the pins that described it went with it. What the
 * header still owes the organiser — the coverage count, and the loud failure
 * line — is what remains under test below.
 */

describe('The header counter keeps its number visible and its sentence in a tooltip', () => {
  it('renders the count as text', () => {
    const wrapper = mountCoverage('counter', {
      participants: [SARA, TOM],
      checkedKeys: ['organization:sara'],
    });

    // A number is scannable at a glance; an icon alone is not.
    expect(wrapper.text()).toContain('1/2');
  });

  it('moves the coverage sentence into the tooltip', () => {
    const wrapper = mountCoverage('counter', {
      participants: [SARA, TOM],
      checkedKeys: ['organization:sara'],
    });

    expect(wrapper.text()).not.toContain('busyTimeCoverage');
    expect(wrapper.find('[title]').attributes('title')).toBe('agenda.eventForm.busyTimeCoverage|1|2');
    expect(wrapper.find('[title]').attributes('aria-label')).toBe('agenda.eventForm.busyTimeCoverage|1|2');
  });

  it('still counts participants and not the guest list', () => {
    // EXO-89850's invariant, unchanged by the redesign: a stale answer about
    // somebody who has left the event must not inflate the count.
    const wrapper = mountCoverage('counter', {
      participants: [SARA],
      checkedKeys: ['organization:sara', 'organization:tom'],
    });

    expect(wrapper.text()).toContain('1/1');
  });
});

describe('The header says out loud what it could not check', () => {
  it('renders the failure as body text, not as a tooltip', () => {
    const wrapper = mountCoverage('report', {
      participants: [SARA],
      failedKeys: ['organization:sara'],
    });

    // THE PIN THAT MATTERS. A tooltip does not exist on touch, and a hover
    // nobody performs teaches nobody that the grid is incomplete.
    expect(wrapper.text()).toContain('agenda.eventForm.busyTimeNotChecked|Sara Green');
  });

  it('renders "shares nothing" as body text too', () => {
    const wrapper = mountCoverage('report', {
      participants: [TOM],
      notDisclosedKeys: ['organization:tom'],
    });

    // Same argument: it is another way for the grid to say nothing about
    // somebody, and it can mislead in exactly the same way.
    expect(wrapper.text()).toContain('agenda.eventForm.busyTimeNotShared|Tom Ray');
  });

  it('tells the two apart rather than merging them into one list', () => {
    const wrapper = mountCoverage('report', {
      participants: [SARA, TOM],
      failedKeys: ['organization:sara'],
      notDisclosedKeys: ['organization:tom'],
    });

    expect(wrapper.text()).toContain('agenda.eventForm.busyTimeNotChecked|Sara Green');
    expect(wrapper.text()).toContain('agenda.eventForm.busyTimeNotShared|Tom Ray');
  });

  it('renders nothing at all when every participant was checked', () => {
    const wrapper = mountCoverage('report', {
      participants: [SARA],
      checkedKeys: ['organization:sara'],
    });

    // The quiet case costs the grid no row and no margin.
    expect(wrapper.html()).toBe('');
  });

  it('keeps the counter even when the report is silent', () => {
    const wrapper = mountCoverage('counter', {
      participants: [SARA],
      checkedKeys: ['organization:sara'],
    });

    // An empty grid may only be read as "free" while something says whose
    // calendars it covers.
    expect(wrapper.text()).toContain('1/1');
  });
});
