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
 * A second round then fixed two things the first left: a gap above the avatar,
 * and the avatar not sharing a centre line with the label. Both came from
 * classes copied from the ordinary block that carry assumptions an avatar
 * breaks — `my-auto`, whose computed value depends on the formatting context,
 * and `d-flex`'s default `align-items: stretch`.
 *
 * WHAT THESE PINS CAN AND CANNOT DO. Geometry is not pinnable here: jsdom
 * computes no layout, so nothing below proves the gap is gone or that the
 * avatar is painted on the label's centre line. Only the rig can show that.
 * What IS pinnable is everything the geometry follows from — which classes the
 * title line and its children carry, that the poisoned `align-center` is
 * absent, that the context-dependent `my-auto` is absent, and that nothing was
 * dropped while rearranging.
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
 * The component's raw source, for the pins that are about what is written
 * rather than about what renders.
 *
 * @returns {String} the .vue file's contents
 */
function componentSource() {
  return fs.readFileSync(path.join(__dirname,
    '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormParticipantBusyItem.vue'), 'utf8');
}

/**
 * The component's source with every comment removed.
 *
 * <p>
 * The pins below are about what the component DOES, and this file deliberately
 * documents the traps it avoids — naming `align-center` and `<p>` in prose to
 * explain why neither is used. Scanning the raw text would make that
 * documentation fail the very pins it explains, so the comments come out
 * first: HTML comments from the template, block and line comments from the
 * script.
 *
 * @returns {String} the source, comments stripped
 */
function componentCode() {
  return componentSource()
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
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
  // An empty list rather than a throw: a mutant that removes the element
  // should fail these pins on their own assertion, not crash the helper and
  // die of an incidental error. The callers assert the list is non-empty, so
  // a silent no-match cannot make a pin vacuous either.
  if (!match) {
    return [];
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

  it('shares the ordinary event title line classes, bar the two it must not', () => {
    // Pinned against the SOURCE of the block it mirrors, so the two cannot be
    // hand-tuned apart: if either title line changes, this fails.
    const ordinary = templateClasses(
      'agenda-common/components/event/form/AgendaEventFormDates.vue',
      /<p\s+:title="storeEventTitle\(eventObj\.event\)"\s+class="([^"]+)"/);
    const busy = templateClasses(
      'agenda-common/components/event/form/AgendaEventFormParticipantBusyItem.vue',
      /<div class="([^"]+)">\s*\n\s*<exo-user-avatar/);

    // Two classes are deliberately not carried over, and both exclusions are
    // asserted below rather than merely filtered out here:
    //   ms-2    the indent, which the avatar's own mx-1 completes from ms-1
    //   my-auto margins whose computed value depends on the formatting context
    expect(ordinary.length).toBeGreaterThan(0);
    expect(busy.length).toBeGreaterThan(0);
    const SHARED = ordinary.filter(name => name !== 'ms-2' && name !== 'my-auto');
    expect(SHARED).toContain('d-flex');
    SHARED.forEach(name => expect(busy).toContain(name));
    expect(busy).toContain('ms-1');
  });

  it('does not carry my-auto, whose effect depends on how much room is left', () => {
    const busy = templateClasses(
      'agenda-common/components/event/form/AgendaEventFormParticipantBusyItem.vue',
      /<div class="([^"]+)">\s*\n\s*<exo-user-avatar/);

    // `.v-application .my-auto` compiles to margin-top/bottom: auto — zero in
    // a block formatting context, free-space distribution in a flex one. A
    // line that must be pinned to the top of its block cannot depend on that.
    expect(busy.length).toBeGreaterThan(0);
    expect(busy).not.toContain('my-auto');
  });

  it('uses a container with no default margin to cancel', () => {
    const source = componentCode();

    // A <p> carries the UA's own 1em margin, which the ordinary block cancels
    // with a `.v-application`-scoped utility. A <div> has nothing to cancel.
    expect(source).not.toMatch(/<p[\s>]/);
  });

  it('never reintroduces the poisoned align-center, anywhere in the component', () => {
    // THE REGRESSION THAT HAS BEEN MADE ONCE. `align-center` resolves to
    // align-items: center !important (vuetify) AND text-align: center (the
    // platform's helpers.less) — the second is what centred this block
    // horizontally. Scanned over the whole file, not just the rendered root,
    // so it cannot come back on any element or in any binding.
    // align-self-center is a different token and is what this component uses.
    expect(componentCode()).not.toMatch(/\balign-center\b/);
  });

  it('centres the avatar and the label against each other without that class', () => {
    const wrapper = mountBlock();

    // d-flex sets display: flex and nothing else, so align-items stays at the
    // default `stretch` and a fixed-size avatar never shares a centre line
    // with a text span. align-self-center compiles to align-self alone.
    expect(wrapper.find('exo-user-avatar').classes()).toContain('align-self-center');
    expect(wrapper.find('span').classes()).toContain('align-self-center');
  });

  it('puts the avatar inside the title line, not floating in the block', () => {
    const wrapper = mountBlock();

    // Inside the <p>, and first: the avatar leads the line the label is on,
    // rather than being centred against the block's whole height.
    const title = wrapper.find('.readonly-event > div');
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
    expect(wrapper.find('.readonly-event > div').classes()).toContain('text-truncate');
    expect(wrapper.find('span').classes()).toContain('text-truncate');
  });

  it('renders the label alone when the participant has no profile to draw', () => {
    const wrapper = mountBlock(Object.assign({}, BUSY_EVENT, {identity: null}));

    expect(wrapper.find('exo-user-avatar').exists()).toBe(false);
    expect(wrapper.text()).toContain('agenda.busy');
  });
});
