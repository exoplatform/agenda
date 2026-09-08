import AgendaEventsDetailsBody from '../../main/webapp/vue-app/agenda-common/components/event/view/AgendaEventsDetailsBody.vue';

/*
 * EXO-89987: on the event detail page a generated conference link was printed
 * in full under the "Join meeting" button. The visible text is now capped at
 * 50 characters followed by an ellipsis; the CTA, the copy button and the
 * title attribute keep the full link.
 */

const MAX_LENGTH = 50;

function computedOn(state) {
  const data = AgendaEventsDetailsBody.data.call({});
  const vm = Object.assign({}, data, state);
  return name => AgendaEventsDetailsBody.computed[name].call(vm);
}

describe('event detail conference link display', () => {
  const longUrl = 'https://meet.example.com/j/1234567890123456789012345678901234567890/abc';

  it('caps the visible link at 50 characters and ends it with an ellipsis', () => {
    expect(longUrl.length).toBeGreaterThan(MAX_LENGTH);
    const get = computedOn({eventConferenceUrl: longUrl});
    expect(get('displayedConferenceUrl')).toBe(`${longUrl.substring(0, MAX_LENGTH)}…`);
  });

  it('keeps a link of exactly 50 characters whole', () => {
    const exact = 'x'.repeat(MAX_LENGTH);
    const get = computedOn({eventConferenceUrl: exact});
    expect(get('displayedConferenceUrl')).toBe(exact);
  });

  it('leaves a short link untouched', () => {
    const get = computedOn({eventConferenceUrl: 'https://short.link/abc'});
    expect(get('displayedConferenceUrl')).toBe('https://short.link/abc');
  });

  it('never splits a multi-byte character at the cut', () => {
    const url = `${'a'.repeat(MAX_LENGTH - 1)}\u{1F4C5}tail`;
    const get = computedOn({eventConferenceUrl: url});
    const shown = get('displayedConferenceUrl');
    expect(shown).toBe(`${'a'.repeat(MAX_LENGTH - 1)}\u{1F4C5}…`);
    // a split pair would leave a lone high surrogate right before the ellipsis
    expect(/[\uD800-\uDBFF]$/.test(shown.slice(0, -1))).toBe(false);
  });

  it('shows nothing when the event has no conference', () => {
    const get = computedOn({eventConferenceUrl: undefined});
    expect(get('displayedConferenceUrl')).toBe('');
  });

  it('still opens the full link from the CTA', () => {
    const get = computedOn({eventConferenceUrl: longUrl});
    expect(get('conferenceHref')).toBe(longUrl);
  });
});
