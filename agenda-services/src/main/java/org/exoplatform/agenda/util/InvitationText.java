/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.util;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Recognises, in a description, the invitation text {@link EventIcsBuilder}
 * itself composes — so that a description which already carries one is not
 * given a second.
 *
 * <p>
 * <b>Why a description can carry one at all.</b> The builder writes its text
 * — attribution, event link, answer links, then the organiser's own words
 * under a label — into every calendar copy and every mailed document. A copy
 * read back into eXo by the CalDAV connector keeps that whole text as the
 * imported event's description, and when two eXo users share one CalDAV
 * account the copy one of them pushed is exactly what the other imports.
 * Their next push handed that description back to the builder, which
 * prepended its text again; the first user imported the result and pushed
 * again; and the object grew by one "Invitation sent by X. Event link:
 * …eventId=N" block per edit, alternating between the two users' event ids
 * (EXO-90227, observed on a rig against Stalwart: two blocks, then three).
 *
 * <p>
 * <b>Replaced, not skipped.</b> When the text handed in already begins with
 * an invitation block, the builder takes that block off and writes its own
 * — rather than leaving the old one and writing nothing. The old block is
 * somebody else's: it names the user who pushed first, in their language,
 * links to <em>their</em> event, and in a calendar copy offers <em>their</em>
 * tokenised answer links, which answer as them. Re-emitting it would hand
 * that person's tokens to every reader of every copy of this event, and
 * attribute the meeting to whoever happened to push first. Writing the
 * current block instead gives each reader what every other copy gives them:
 * the attribution and the links minted for them.
 *
 * <p>
 * <b>Any eXo's block, by shape and not by words.</b> The labels are read in
 * the recipient's locale, and the builder reading a description back cannot
 * know which locale wrote it — nor which deployment, nor which event id. So
 * no word of any translation is matched. What is matched is the layout the
 * builder produces, and all four of its joints must be there:
 * <ol>
 * <li>a line that is a <b>short label</b> — at most
 * {@link #LABEL_MAX_LENGTH} characters, which is what tells a label from a
 * sentence — then whitespace, then <b>one eXo event link and nothing
 * else</b>;</li>
 * <li>that line as the <b>second or third</b> non-blank line of the text,
 * after the attribution sentence and at most one conference line;</li>
 * <li>optionally an answer block under it: a prompt, then one to three lines
 * each <b>carrying a tokenised answer link</b> — the one thing in this layout
 * nobody can type, so nothing else is asked of those lines;</li>
 * <li>then either <b>nothing at all</b>, or a line that <b>has the shape of a
 * label</b> — the one under which the builder puts the organiser's text.</li>
 * </ol>
 * Everything up to and including that label line is the block; what follows
 * is the organiser's text. Miss any joint and the text is returned exactly as
 * given.
 *
 * <p>
 * <b>And one invariant over the whole of it, checked on the way out.</b> A
 * reading that would hand back a text still carrying an answer link is refused
 * outright, and the text is returned as given: see {@link #stripFrom}. Joints
 * are a hypothesis about a layout, and a hypothesis can be wrong in a way
 * nobody has thought of yet; that one token must not be re-emitted as the
 * organiser's own words is not a hypothesis, so it is asserted on the result
 * rather than inferred from the joints.
 *
 * <p>
 * <b>What is accepted as a false negative.</b> Every one of these leaves the
 * text untouched, which is to say it would still stack — the price of reading
 * shape rather than words, and paid in the direction that loses nothing:
 * <ul>
 * <li>A block whose event link the pusher withheld — a guest recipient, or a
 * deployment whose portal could not be asked for its address — has no link
 * line. Neither case reaches this path today: the connector writes copies for
 * eXo users only, and a copy with no link is one this deployment did not
 * compose.</li>
 * <li>A link line as the fourth or later non-blank line, on purpose
 * (below).</li>
 * <li><b>A label the bundle answers blank — guarded, and listed here so it
 * stays guarded.</b> {@code description()} would write
 * {@code "" + " " + url} and the line would carry no label at all, so joint 1
 * would not be met. {@link EventIcsBuilder}'s own label lookup answers the key
 * when the bundle answers blank instead, and the line keeps a label whatever
 * Crowdin ships. Not idle plumbing — {@code agenda.eventLink} and
 * {@code agenda.rsvpPrompt} are in {@code Agenda_en.properties} alone, and
 * every other locale reads them through the English parent bundle
 * ({@code BaseResourceBundleService}, whose default locale is {@code en}),
 * while {@code Agenda_sq.properties} already ships an untranslated Crowdin
 * placeholder as its {@code agenda.eventDetail}.</li>
 * <li><b>A detail label that does not end in a colon.</b> 38 of the 39
 * bundles that translate {@code agenda.eventDetail} end it with one; the
 * exception is that Albanian placeholder. A locale whose label loses its colon
 * loses recognition with it, for that locale's readers only.</li>
 * <li><b>A server that inserts a blank line the builder did not write</b>,
 * between the answer prompt and the answer links. {@link #pastAnswerBlock}
 * reads the answers on the lines immediately under the prompt — the builder
 * writes them with one newline apiece and none observed server folds a line
 * <em>apart</em> — so a blank line there strands the answer links below the
 * point the block would end at, and the invariant then refuses the reading
 * altogether rather than returning them. Fail-closed, and the pin
 * {@code noPartialStripEverKeepsSomebodyElsesAnswerToken} is what keeps it
 * that way.</li>
 * <li><b>A server that folds a line the builder wrote onto another</b> — the
 * attribution onto the link line, which {@link #eventLinkLine} stops looking
 * for at the first non-blank line, or the detail label onto the first line of
 * the text, which then ends in that text rather than in a colon. BlueMind,
 * the one server observed folding anything, folds blank lines only and keeps
 * both apart.</li>
 * <li><b>The occurrence link shape.</b> {@code NotificationUtils} also
 * composes a link carrying {@code parentId=} and {@code occurrenceId=} for one
 * occurrence of a recurrent event, which the link pattern does not match — it
 * requires {@code eventId=} and then the end of the line. <b>Unreachable today
 * and a deliberate guard, not a description of the code:</b> every path into the
 * builder passes the URL from {@link EventIcsBuilder#eventUrl(long)}, which
 * writes {@code ?eventId=}. It is written down because the coupling that
 * would make it reachable lives in another class — one caller away — and this
 * ledger is where that caller's author would look.</li>
 * </ul>
 *
 * <p>
 * <b>What keeps this from destroying text a person typed.</b> A link to an
 * eXo event pasted into a sentence, a link on the first line, a link with
 * words after it or a full stop after it, a bare link on a line of its own, a
 * link in the fourth paragraph, a link under a sentence rather than a label, a
 * list of several event links, a link followed by a line of prose: none has
 * the shape, and the very instance given is returned. What remains is a person
 * writing, by hand and in this order, a one-line paragraph, then a short
 * label and an eXo event link alone on a line, then a line that itself ends in
 * a colon or is a bundle key of ours, and then their text — and losing
 * everything down to that line. It is the price of reading no words, it is
 * bounded to the head of the text, and it is one shape narrower than a
 * reader would guess: <b>the joint that does the work is the last one</b>,
 * because a person's next line after a link is prose, and prose does not end
 * in a colon.
 *
 * <p>
 * <b>What a false positive costs, so the price is on the page.</b> The block is
 * recognised in the text, not in the markup, so when it is recognised the
 * remainder of a rich description is re-rendered from plain text — see
 * {@link EventIcsBuilder}'s HTML flavour: bold, links and lists a person wrote
 * in the editor arrive as words. On a true positive that costs nothing (the
 * text came in as plain text). On a false positive it is a second loss on top
 * of the deleted lines. Widening any joint above is therefore not a trade of
 * recall against a little noise; read both this paragraph and that method's
 * before touching one.
 *
 * <p>
 * <b>Fails closed, and the one outcome that would not be is refused
 * explicitly.</b> Anything not recognised is returned untouched, so the worst
 * outcome for a text this cannot read is the behaviour before this class
 * existed. That is <em>not</em> true of a <b>partial</b> reading — one that
 * ends the block above somebody else's answer links and hands those back as
 * the organiser's text — which is strictly worse than both outcomes: the
 * tokens are re-emitted <em>and</em> the attribution that said whose they were
 * is deleted, so no reader can tell. {@link #stripFrom} therefore asserts on
 * its result that this cannot happen, and returns the text as given when it
 * would. The coupling is to the builder's own layout — which lives in
 * the same package and is pinned twice, because one pin cannot see the whole
 * of it: {@code InvitationTextTest.renderingWhatTheBuilderRenderedIsTheSameCopy}
 * renders <b>outside a container</b>, where every label degrades to its bundle
 * key, so it fails on a drift in the <em>layout</em> — a line moved, a joint
 * removed — and cannot see a drift that needs a real label to show, and
 * {@code renderingWithLabelsFromABundleIsTheSameCopy} renders the same layout
 * with the labels read from a bundle, so the localised shape is covered too. A
 * drift visible in neither — a translation reshaped in Crowdin — is what the
 * false-negative ledger above is for.
 *
 * <p>
 * <b>That coupling crosses a repository, and a change to the layout is
 * therefore a two-repository change.</b> The connector calls this on the way
 * IN — {@code caldav-integration}'s {@code IcsEventMapper.toEvent} — so that
 * the description eXo <em>stores</em> carries the organiser's text alone; the
 * builder calls it on the way OUT, so that every channel it renders carries one
 * block.
 * Neither call site can replace the other: the builder never touches what the
 * event drawer, the mail body and search read, and the import never re-reads
 * an object nobody edits again. What the pin above cannot see is the import
 * half — a layout change would degrade it silently, fail-closed, with no test
 * in this repository noticing. Change the layout and the recogniser together,
 * and say so in the connector's PR.
 */
public final class InvitationText {

  /**
   * How long a label may be, which is one half of what tells one from a
   * sentence.
   *
   * <p>
   * Measured, not guessed, and stated for the labels this bound is actually
   * asked about — the two the patterns read a label at:
   * {@code agenda.eventLink} on the link line and {@code agenda.eventDetail}
   * on the line that closes the block. Across the 40 {@code Agenda_*}
   * bundles under <code>agenda-webapps/.../locale/portlet</code>, the longest
   * value either is ever given is <b>30</b> characters — an untranslated
   * Crowdin placeholder in <code>Agenda_sq.properties</code> — and the longest
   * real translation is 24 (<code>Détails de l'événement :</code>, French,
   * {@code agenda.eventDetail}; {@code agenda.eventLink} is English-only and
   * 11). The keys themselves, which {@link EventIcsBuilder} writes when no
   * bundle can be read, are 16 and 18. Forty leaves a third again of room for a
   * translation nobody has written yet and still refuses a sentence, which is
   * the point: a label is short, and prose is not.
   *
   * <p>
   * For the whole layout — all nine of its labels, including the answer labels
   * this bound is deliberately <em>not</em> applied to (see
   * {@link #ANSWER_LINK}) — the figures are 30 for the placeholder and <b>28</b>
   * for the longest real translation (<code>Lien de la visioconférence :</code>,
   * French {@code agenda.visioLink}), with the keys 14 to 21. Named here
   * because a reader widening the bound will reach for the layout's numbers,
   * not this bound's.
   */
  private static final int     LABEL_MAX_LENGTH  = 40;

  /**
   * A label at the head of a line, and the whitespace separating it from what
   * it introduces — bounded by {@link #LABEL_MAX_LENGTH}, so a sentence with a
   * link at the end of it is not read as a label with a link after it.
   */
  private static final String  LABEL_THEN        = "^\\s*\\S.{0," + (LABEL_MAX_LENGTH - 1) + "}?\\s+";

  /**
   * At most a server's bracketed repetition of the link just matched, which
   * BlueMind appends to every URI in a description, and then the end of the
   * line: nothing else may follow, which is what keeps a link a person wrote
   * words after out of the pattern below.
   */
  private static final String  AND_NOTHING_ELSE  = "(?:\\s+<\\1>)?\\s*$";

  /**
   * The line naming the event in eXo: a label, one eXo event link — the
   * shape {@link NotificationUtils#getEventURL(long)} produces, with or
   * without a scheme, since a copy has been read back without one — and
   * nothing else.
   *
   * <p>
   * The label is required, must precede the link with whitespace, and must be
   * short: the builder always writes one, if only the bundle key when no
   * bundle can be read; a bare link on a line is what a person pastes, and a
   * sentence ending in a link is what a person writes.
   */
  private static final Pattern EVENT_LINK_LINE   =
                                                Pattern.compile(LABEL_THEN
                                                    + "((?:https?://)?[^\\s<>\"']+/portal/[^/\\s<>?]+/agenda\\?eventId=\\d+)"
                                                    + AND_NOTHING_ELSE, Pattern.CASE_INSENSITIVE);

  /**
   * A tokenised answer link, as {@link NotificationUtils#getResponseURL} mints
   * it, <b>anywhere on a line</b> — our own REST route, carrying a token that
   * authenticates as the person it was minted for.
   *
   * <p>
   * <b>Nothing else is asked of the line, on purpose, and that is a
   * correction.</b> Requiring a short label in front and nothing behind — as
   * the answer lines were read until EXO-90228 — bought no discrimination this
   * link does not already carry by itself (an organiser does not hand-type a
   * minted token), and it cost recall in the one direction that matters: a
   * locale whose answer label ran past {@link #LABEL_MAX_LENGTH}, or a server
   * that rewrote the bracketed repetition, left the answer lines unread, ended
   * the block one line too high, and handed those tokens back as the
   * organiser's text. Read loosely, they are consumed whatever a bundle or a
   * server does to them; and the same pattern, applied to the whole of what is
   * about to be returned, is the invariant in {@link #stripFrom}.
   */
  private static final Pattern ANSWER_LINK       =
                                                Pattern.compile("/portal/rest/v1/agenda/events/\\d+/response/send\\?",
                                                                Pattern.CASE_INSENSITIVE);

  /**
   * A bundle key of this addon's own namespace, which is what
   * {@link EventIcsBuilder} writes in a label's place when no bundle can be
   * read at all.
   *
   * <p>
   * The one word-like shape this class matches, and it is not a word in
   * anybody's language: it is a literal of our own, locale-independent by
   * construction. Without it the block a builder outside a portal container
   * composes — every label degraded to its key, the shape
   * {@code InvitationTextTest.renderingWhatTheBuilderRenderedIsTheSameCopy}
   * renders — would not be recognised as its own.
   */
  private static final Pattern LABEL_KEY         = Pattern.compile("^agenda\\.[A-Za-z]+$");

  /**
   * How many non-blank lines may precede the event link line: the attribution
   * sentence and the conference line. The builder writes nothing else before
   * it.
   */
  private static final int     LINES_BEFORE_LINK = 2;

  /** The answers the builder can offer, which bounds the block listing them. */
  private static final int     ANSWERS_OFFERED   = 3;

  /** A line break as any writer or server spells it. */
  private static final Pattern LINE_BREAK        = Pattern.compile("\\R");

  /**
   * Not instantiable: a reader of text, holding no state.
   */
  private InvitationText() {
    // Utility class.
  }

  /**
   * The text without the invitation block composed in front of it, or the
   * text exactly as given when it carries none.
   *
   * <p>
   * Applied until nothing more is recognised: a description that already
   * stacked several blocks before this existed — the rig held three — renders
   * with the organiser's text alone, not with one block fewer.
   *
   * <p>
   * <b>The one thing this promises, whatever the joints did.</b> Either the
   * text is returned as given, or what is returned carries <b>no answer
   * link</b> — never a reading that took a block off and kept somebody else's
   * tokens under the organiser's name. The joints are meant to make that
   * unreachable; this makes it so. Two ways in were known when it was written
   * (an answer label longer than a label, a server rewriting the bracketed
   * repetition), both closed at {@link #ANSWER_LINK} as well, and one was
   * found while writing this guard (a blank line inserted between the prompt
   * and the answers) — which is the argument for asserting the outcome instead
   * of trusting the enumeration.
   *
   * <p>
   * Refusing is the milder failure and not a free one: the text keeps its old
   * block, so this event's copies stack one more, exactly as they did before
   * this class existed. That is the price of not laundering one person's
   * authentication token into another person's mail
   * ({@code AgendaEventRest} honours it with no session at all), and it is not
   * close.
   *
   * @param text the description as plain text, null and blank tolerated
   * @return what remains once every leading block is taken off; an empty
   *         string when the block was all there was; the input itself when no
   *         block is recognised, or when the reading would have kept an answer
   *         link
   */
  public static String stripFrom(String text) {
    if (StringUtils.isBlank(text)) {
      return text;
    }
    String remaining = text;
    boolean strippedABlock = false;
    String stripped = stripOnce(remaining);
    while (stripped != null) {
      remaining = stripped;
      strippedABlock = true;
      stripped = stripOnce(remaining);
    }
    if (strippedABlock && ANSWER_LINK.matcher(remaining).find()) {
      return text;
    }
    return remaining;
  }

  /**
   * Takes one block off the front of the text.
   *
   * <p>
   * Line by line, because a server folds the blank lines the builder writes:
   * BlueMind returns {@code Chemistry.\n\nEvent link: …} as
   * {@code Chemistry.\n Event link: …}, so a paragraph cannot be relied on but
   * a line still can. Blank lines are skipped wherever they may or may not be.
   *
   * @param text a description that may begin with a block
   * @return the text past the block, trimmed of blank lines at both ends;
   *         null when the text does not begin with one
   */
  private static String stripOnce(String text) {
    List<String> lines = List.of(LINE_BREAK.split(text));
    int link = eventLinkLine(lines);
    if (link < 0) {
      return null;
    }
    int next = pastAnswerBlock(lines, nextNonBlank(lines, link + 1));
    next = nextNonBlank(lines, next);
    if (next >= lines.size()) {
      return "";
    }
    // The line under which the builder puts the organiser's own text is a
    // label, written only when there is text to introduce - so the block ends
    // one of two ways: either nothing follows the links at all (above), or what
    // follows is that label. A line here that is not one is a line this class
    // did not write, and the whole text is left alone. This is a claim about
    // the layout, not a proof about the text - which is why stripFrom checks
    // what this returns rather than relying on the claim (EXO-90228).
    if (!looksLikeALabel(lines.get(next))) {
      return null;
    }
    return trimBlankLines(lines.subList(next + 1, lines.size()));
  }

  /**
   * Whether a line is a label introducing what comes under it, rather than
   * something a person wrote.
   *
   * <p>
   * This is the guard between "the block ended here" and "the user's second
   * paragraph began here", and it is the only thing standing between the
   * recogniser and a line of somebody's text: {@link #stripOnce} drops the
   * line it is asked about. Two shapes pass, and they are the two the builder
   * writes: a short line ending in a colon, which is what every translated
   * bundle ships ({@code Event details:}, {@code Détails de l'événement :}),
   * and a bare bundle key of this addon ({@link #LABEL_KEY}), which is what it
   * writes when no bundle can be read. A sentence is too long, or does not end
   * in a colon, or both.
   *
   * @param line the line after the links, already known to be non blank
   * @return true when it has the shape of a label
   */
  private static boolean looksLikeALabel(String line) {
    String label = line.strip();
    return label.length() <= LABEL_MAX_LENGTH && (label.endsWith(":") || LABEL_KEY.matcher(label).matches());
  }

  /**
   * Where the event link line is, when the text begins like a block.
   *
   * @param lines the text
   * @return the index of the line that is a label and an eXo event link, when
   *         it is the second or third non-blank line; -1 otherwise
   */
  private static int eventLinkLine(List<String> lines) {
    int nonBlankBefore = 0;
    for (int i = 0; i < lines.size(); i++) {
      if (StringUtils.isBlank(lines.get(i))) {
        continue;
      }
      if (EVENT_LINK_LINE.matcher(lines.get(i)).matches()) {
        return nonBlankBefore >= 1 ? i : -1;
      }
      nonBlankBefore++;
      if (nonBlankBefore > LINES_BEFORE_LINK) {
        return -1;
      }
    }
    return -1;
  }

  /**
   * Skips the block of answer links, when there is one.
   *
   * <p>
   * The block is a prompt line followed by one to three lines each carrying an
   * answer link; it is consumed only when at least one such line follows the
   * prompt, so a lone line after the link — the label over the organiser's
   * text — is never taken for a prompt.
   *
   * <p>
   * The lines are read <b>immediately</b> under the prompt, blank lines
   * included in nothing: the builder writes one newline apiece, so a blank line
   * there is not the layout. It is also not fatal — the answer links then stay
   * below where the block would end, and {@link #stripFrom}'s invariant refuses
   * the whole reading rather than returning them.
   *
   * @param lines the text
   * @param from the first non-blank line after the event link line
   * @return the index past the block, or {@code from} itself when the line
   *         there does not begin one
   */
  private static int pastAnswerBlock(List<String> lines, int from) {
    int answers = 0;
    int i = from + 1;
    while (i < lines.size() && answers < ANSWERS_OFFERED && ANSWER_LINK.matcher(lines.get(i)).find()) {
      answers++;
      i++;
    }
    return answers == 0 ? from : i;
  }

  /**
   * Skips the blank lines a server may or may not have left, so every step
   * through the block reasons about the lines that carry something.
   *
   * @param lines the text
   * @param from where to start looking
   * @return the index of the first non-blank line at or after {@code from},
   *         or the size of the list when there is none
   */
  private static int nextNonBlank(List<String> lines, int from) {
    int i = from;
    while (i < lines.size() && StringUtils.isBlank(lines.get(i))) {
      i++;
    }
    return i;
  }

  /**
   * Joins lines back into text, without the blank lines at either end and
   * without the layout a server's folding may have left around the whole.
   *
   * @param lines the lines to keep
   * @return the text, empty when every line was blank
   */
  private static String trimBlankLines(List<String> lines) {
    int start = nextNonBlank(lines, 0);
    int end = lines.size();
    while (end > start && StringUtils.isBlank(lines.get(end - 1))) {
      end--;
    }
    return String.join("\n", lines.subList(start, end)).strip();
  }
}
