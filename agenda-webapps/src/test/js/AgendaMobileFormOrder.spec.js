import {mount} from '@vue/test-utils';
import AgendaEventMobileForm from '../../main/webapp/vue-app/agenda-common/components/event/form/mobile/AgendaEventMobileForm.vue';

/*
 * EXO-89852, the mobile event form's running order.
 *
 * It carried the same appended-participants order the quick-add drawer did —
 * attendees last, under location, colour and conference. Same defect, on the
 * screen with the least room of the three: on a phone every field above the
 * participants is a scroll between deciding when and deciding who.
 *
 * Worked out on this form's own merits rather than copied from the drawer,
 * because this form is not shaped like it:
 *
 *   - It has a RECURRENCE block the drawer has none of, and recurrence is part
 *     of WHEN, not a separate decision. Participants therefore follow the
 *     whole when-cluster — dates AND recurrence — rather than splitting the
 *     dates from the rule that repeats them.
 *   - It has NO date-poll link. There is no date step on mobile, so there is
 *     nothing to link to; that absence is deliberate and stays.
 *   - It ends with the "attendees may update" switch and its description. That
 *     is a decision about RIGHTS, not about who is coming, it is rarely
 *     touched, and it carries a paragraph — so it stays last rather than
 *     following the participants up. Considered and rejected, not overlooked.
 */

/**
 * The form's fields, in the order the DOM holds them.
 *
 * Labels are skipped: this form captions each control with its own <label>, so
 * the captions would double every entry without saying anything the control
 * does not. Anything else it cannot name is returned as unrecognised rather
 * than dropped, so a field appended in the wrong place fails rather than
 * passing unseen.
 *
 * @param {Object} form the mounted form
 * @returns {Array} the field names, top to bottom
 */
function fieldOrder(form) {
  const body = form.element.querySelector('.flex-column.flex-grow-1');
  return Array.prototype.map.call(body.children, node => {
    const tag = node.tagName.toLowerCase();
    if (tag === 'label') {
      return null;
    } else if (node.id === 'eventTitle') {
      return 'title';
    } else if (node.id === 'eventLocation') {
      return 'location';
    } else if (tag === 'agenda-event-form-destination') {
      return 'destination';
    } else if (tag === 'agenda-event-form-date-pickers') {
      return 'date';
    } else if (tag === 'agenda-event-form-recurrence') {
      return 'recurrence';
    } else if (tag === 'agenda-event-recurrence') {
      return 'recurrenceSummary';
    } else if (tag === 'agenda-event-form-attendees') {
      return 'participants';
    } else if (tag === 'agenda-event-form-color-picker') {
      return 'colour';
    } else if (tag === 'agenda-event-form-conference') {
      return 'conference';
    } else if (node.querySelector && node.querySelector('v-switch')) {
      return 'updatePermission';
    } else if (node.textContent.includes('agenda.modifyEventPermissionDescription')) {
      return 'updatePermissionDescription';
    }
    return `unrecognised:${tag}`;
  }).filter(name => name !== null);
}

/**
 * Mounts the mobile form.
 *
 * @param {Object} event the event being edited
 * @returns {Object} the mounted wrapper
 */
function mountMobileForm(event) {
  /*
   * Mounted with its mounted() hook neutralised. That hook calls
   * resetCustomValidity(), which reaches through a ref into the destination
   * component for a method of its own — and here that component is an unknown
   * element, as every child is in this suite. Neutralising the hook is what
   * lets every field render under its real tag, so the reader below names them
   * by what the shipped template contains rather than by anything the harness
   * introduced. created() only subscribes to the root bus and is left alone.
   */
  const MobileForm = Object.assign({}, AgendaEventMobileForm, {mounted() {}});
  return mount(MobileForm, {
    propsData: {
      event: event || {
        summary: 'Sprint review',
        calendar: {owner: {id: '7'}},
        attendees: [],
        dateOptions: [],
      },
      currentSpace: null,
      calendars: [],
      conferenceProvider: null,
    },
    mocks: {
      $t: key => key,
      $agendaUtils: {toRFC3339: date => date, USER_TIMEZONE_ID: 'UTC'},
      $utils: {htmlToText: html => html || ''},
    },
  });
}

describe('EXO-89852 — the mobile form asks who before it asks where', () => {
  it('puts the participants with the date, above the optional attributes', () => {
    expect(fieldOrder(mountMobileForm())).toEqual([
      'title',
      'destination',
      'date',
      'recurrence',
      'participants',
      'location',
      'colour',
      'conference',
      'updatePermission',
      'updatePermissionDescription',
    ]);
  });

  /*
   * The relationships the order exists for, named so a failure says which one
   * broke rather than only that the list changed.
   */
  it('keeps participants below the whole when-cluster and above every attribute', () => {
    const order = fieldOrder(mountMobileForm());

    expect(order.indexOf('participants')).toBeGreaterThan(order.indexOf('recurrence'));
    expect(order.indexOf('participants')).toBeLessThan(order.indexOf('location'));
    expect(order.indexOf('participants')).toBeLessThan(order.indexOf('colour'));
    expect(order.indexOf('participants')).toBeLessThan(order.indexOf('conference'));
  });

  /*
   * Recurrence is part of when, so the rule and the dates it repeats must not
   * be split by the participants landing between them.
   */
  it('does not split the dates from the recurrence that repeats them', () => {
    const order = fieldOrder(mountMobileForm());

    expect(order[order.indexOf('date') + 1]).toBe('recurrence');
  });

  /*
   * The recurrence summary renders only for a recurrent event, and when it
   * does it belongs with the rule, not adrift among the participants.
   */
  it('keeps the recurrence summary with its rule when the event repeats', () => {
    const order = fieldOrder(mountMobileForm({
      summary: 'Standup',
      calendar: {owner: {id: '7'}},
      attendees: [],
      dateOptions: [],
      recurrence: {type: 'DAILY'},
    }));

    expect(order[order.indexOf('recurrence') + 1]).toBe('recurrenceSummary');
    expect(order.indexOf('recurrenceSummary')).toBeLessThan(order.indexOf('participants'));
  });

  /*
   * There is no date step on mobile, so there is nothing for a date-poll link
   * to open. Its absence is deliberate; this pins it against being copied over
   * from the quick-add drawer by symmetry.
   */
  it('offers no date-poll link, there being no date step to open', () => {
    expect(mountMobileForm().text()).not.toContain('agenda.alternativeDates');
  });
});
