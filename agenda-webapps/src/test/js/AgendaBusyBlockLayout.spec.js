import fs from 'fs';
import path from 'path';
import {mount} from '@vue/test-utils';
import AgendaEventFormParticipantBusyItem
  from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormParticipantBusyItem.vue';
import * as agendaUtils from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';

/*
 * EXO-89850, the Busy block's layout.
 *
 * Benjamin: "the Busy event UI is not well aligned. The icon should be top
 * left and left align like the other events."
 *
 * The block carried `d-flex align-center` on its root. In this skin the
 * align-center utility ALSO sets text-align: center — agenda.less documents
 * that trap twice already — so the block was centred on both axes while every
 * other block in the same grid is anchored top-left, and the avatar sat
 * vertically centred against the whole block instead of on the title line.
 *
 * WHAT THESE PINS CAN AND CANNOT DO. Geometry is not pinnable here: jsdom
 * computes no layout, so nothing below proves the avatar is at the top left of
 * a painted rectangle. Only the rig can show that. What IS pinnable is
 * everything the geometry follows from — that the block reuses the ordinary
 * event's container and class list rather than a hand-tuned one of its own,
 * that the avatar is inside the title line, that the centring classes are
 * gone, and that nothing was dropped while rearranging.
 */

const BUSY_EVENT = {
  type: 'participantBusy',
  summary: 'Busy',
  identity: {id: '400', providerId: 'organization', remoteId: 'bm', profile: {id: '400', fullname: 'B M'}},
  startDate: new Date('2026-07-20T10:00:00'),
  endDate: new Date('2026-07-20T12:30:00'),
};

/**
 * A block too short for two lines, by the same 60-minute test the ordinary
 * event applies.
 *
 * @returns {Object} a half-hour busy block
 */
function shortBusyEvent() {
  return Object.assign({}, BUSY_EVENT, {
    startDate: new Date('2026-07-20T10:00:00'),
    endDate: new Date('2026-07-20T10:30:00'),
  });
}

/**
 * Mounts the busy block.
 *
 * @param {Object} busyEvent the block to render
 * @returns {Object} the mounted wrapper
 */
function mountBlock(busyEvent) {
  return mount(AgendaEventFormParticipantBusyItem, {
    propsData: {busyEvent: busyEvent || BUSY_EVENT},
    mocks: {
      $agendaUtils: agendaUtils,
      $t: key => key,
    },
  });
}

/**
 * The class list of the first element matching a pattern in a .vue file's
 * template source.
 *
 * @param {String} file the component file, relative to vue-app
 * @param {RegExp} pattern a pattern whose first group is the class attribute
 * @returns {Array} the classes
 */
function templateClasses(file, pattern) {
  const source = fs.readFileSync(
    path.join(__dirname, '../../main/webapp/vue-app', file), 'utf8');
  const match = source.match(pattern);
  if (!match) {
    throw new Error(`no element matching ${pattern} in ${file}`);
  }
  return match[1].split(/\s+/).filter(name => !!name);
}

describe('The Busy block is laid out like the ordinary events beside it', () => {
  it('reuses the ordinary event container rather than one of its own', () => {
    const wrapper = mountBlock();

    // `.readonly-event` is what agenda.less sizes to the block (height: 100%);
    // a private root class would have to re-derive that.
    expect(wrapper.classes()).toContain('readonly-event');
  });

  it('carries no centring utility, on either axis', () => {
    const wrapper = mountBlock();
    const root = wrapper.classes();

    // THE DEFECT. align-center centres vertically AND, in this skin, sets
    // text-align: center, which is where the horizontal centring came from.
    expect(root).not.toContain('align-center');
    expect(root).not.toContain('justify-center');
    expect(root).not.toContain('text-center');
  });

  it('shares the ordinary event title line class for class', () => {
    // Pinned against the SOURCE of the block it mirrors, so the two cannot be
    // hand-tuned apart: if either title line changes, this fails.
    const ordinary = templateClasses(
      'agenda-common/components/event/form/AgendaEventFormDates.vue',
      /<p\s+:title="storeEventTitle\(eventObj\.event\)"\s+class="([^"]+)"/);
    const busy = templateClasses(
      'agenda-common/components/event/form/AgendaEventFormParticipantBusyItem.vue',
      /<p class="([^"]+)">/);

    // Every layout class the ordinary title carries, the busy title carries.
    // The indent differs by design and by four pixels: the busy title takes
    // ms-1 and the avatar's own wrapper adds mx-1, landing on the ordinary
    // title's ms-2.
    ordinary.filter(name => name !== 'ms-2').forEach(name => expect(busy).toContain(name));
    expect(busy).toContain('ms-1');
  });

  it('puts the avatar inside the title line, not floating in the block', () => {
    const wrapper = mountBlock();

    // Inside the <p>, and first: the avatar leads the line the label is on,
    // rather than being centred against the block's whole height.
    const title = wrapper.find('p');
    expect(title.find('exo-user-avatar').exists()).toBe(true);
    expect(title.element.firstElementChild.tagName.toLowerCase()).toBe('exo-user-avatar');
  });

  it('still shows both the label and the time', () => {
    const wrapper = mountBlock();

    expect(wrapper.text()).toContain('agenda.busy');
    expect(wrapper.findAll('date-format')).toHaveLength(2);
  });

  it('drops the time on a short block, keeping the avatar and the label', () => {
    const wrapper = mountBlock(shortBusyEvent());

    // The same isShortEvent test the ordinary event uses: one line in a
    // half-hour block, in both, rather than two lines overflowing its height.
    expect(wrapper.findAll('date-format')).toHaveLength(0);
    expect(wrapper.find('exo-user-avatar').exists()).toBe(true);
    expect(wrapper.text()).toContain('agenda.busy');
  });

  it('truncates rather than pushing the time out when the label is long', () => {
    const wrapper = mountBlock();

    // The avatar keeps its size and the label gives way — text-truncate on
    // both the line and the label, exactly as on the ordinary event.
    expect(wrapper.find('p').classes()).toContain('text-truncate');
    expect(wrapper.find('p span').classes()).toContain('text-truncate');
  });

  it('renders the label alone when the participant has no profile to draw', () => {
    const wrapper = mountBlock(Object.assign({}, BUSY_EVENT, {identity: null}));

    expect(wrapper.find('exo-user-avatar').exists()).toBe(false);
    expect(wrapper.text()).toContain('agenda.busy');
  });
});
