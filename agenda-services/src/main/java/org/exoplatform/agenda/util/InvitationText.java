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
 * no word is matched. What is matched is the layout the builder produces,
 * anchored on the one line of it that has a shape of its own: a label and an
 * eXo event link and nothing else, as the second or third non-blank line of
 * the text, after the attribution sentence and at most one conference line.
 * Everything up to that line is the block; an answer block after it — a
 * prompt and one to three lines each ending in an answer link — is part of
 * it; the line after that is the label under which the organiser's text
 * sits, and what follows is that text.
 *
 * <p>
 * <b>What is accepted as a false negative.</b> A block whose event link the
 * pusher withheld — a guest recipient, or a deployment whose portal could
 * not be asked for its address — has no link line and is not recognised;
 * such a description would still stack. Neither case reaches this path
 * today: the connector writes copies for eXo users only, and a copy with no
 * link is one this deployment did not compose. A link line as the fourth or
 * later non-blank line is not recognised either, on purpose (below).
 *
 * <p>
 * <b>What keeps this from destroying text a person typed.</b> A link to an
 * eXo event pasted into a sentence, a link on the first line, a link with
 * words after it, a bare link on a line of its own, a link in the fourth
 * paragraph: none has the shape and the text is returned exactly as given.
 * The exposure that remains is a person typing, by hand, a one-line
 * paragraph, then a paragraph that is a short label and an eXo event link,
 * then more — the block's own shape — and losing those first two paragraphs
 * plus one line for it. Nobody has done that; it is the price of reading no
 * words, and it is bounded to the head of the text.
 *
 * <p>
 * <b>Fails closed.</b> Anything not recognised is returned untouched, so the
 * worst outcome for a text this cannot read is the behaviour before this
 * class existed. The coupling is to the builder's own layout — which lives in
 * the same package and is pinned by
 * {@code InvitationTextTest.renderingWhatTheBuilderRenderedIsTheSameCopy}, the
 * test that fails if the layout and the recogniser drift apart.
 *
 * <p>
 * <b>That coupling crosses a repository, and a change to the layout is
 * therefore a two-repository change.</b> The connector calls this on the way
 * IN — {@code caldav-integration}'s {@code IcsEventMapper.toEvent} — so that
 * the description eXo *stores* carries the organiser's text alone; the builder
 * calls it on the way OUT, so that every channel it renders carries one block.
 * Neither call site can replace the other: the builder never touches what the
 * event drawer, the mail body and search read, and the import never re-reads
 * an object nobody edits again. What the pin above cannot see is the import
 * half — a layout change would degrade it silently, fail-closed, with no test
 * in this repository noticing. Change the layout and the recogniser together,
 * and say so in the connector's PR.
 */
public final class InvitationText {

  /**
   * The line naming the event in eXo: a label, one eXo event link — the
   * shape {@link NotificationUtils#getEventURL(long)} produces, with or
   * without a scheme, since a copy has been read back without one — and at
   * most a server's bracketed repetition of that same link, which BlueMind
   * appends to every URI in a description.
   *
   * <p>
   * The label is required and must precede the link with whitespace: the
   * builder always writes one, if only the bundle key when no bundle can be
   * read, and a bare link on a line is what a person pastes.
   */
  private static final Pattern EVENT_LINK_LINE   =
                                                Pattern.compile("^\\s*\\S.*?\\s+((?:https?://)?[^\\s<>\"']+/portal/[^/\\s<>?]+/agenda\\?eventId=\\d+)"
                                                    + "(?:\\s+<\\1>)?\\s*$",
                                                                Pattern.CASE_INSENSITIVE);

  /**
   * One offered answer: a label and a tokenised answer link, as
   * {@link NotificationUtils#getResponseURL} mints it. A server's bracketed
   * repetition of the link is tolerated for the same reason as above.
   */
  private static final Pattern ANSWER_LINK_LINE  =
                                                 Pattern.compile("^\\s*\\S.*?\\s+((?:https?://)?[^\\s<>\"']+/portal/rest/v1/agenda/events/\\d+/response/send\\?[^\\s<>\"']*)"
                                                     + "(?:\\s+<\\1>)?\\s*$",
                                                                 Pattern.CASE_INSENSITIVE);

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
   * @param text the description as plain text, null and blank tolerated
   * @return what remains once every leading block is taken off; an empty
   *         string when the block was all there was; the input itself when no
   *         block is recognised
   */
  public static String stripFrom(String text) {
    if (StringUtils.isBlank(text)) {
      return text;
    }
    String remaining = text;
    String stripped = stripOnce(remaining);
    while (stripped != null) {
      remaining = stripped;
      stripped = stripOnce(remaining);
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
    // label, written only when there is text to introduce - so a line here is
    // that label, and what follows it is the description.
    return trimBlankLines(lines.subList(next + 1, lines.size()));
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
   * The block is a prompt line followed by one to three lines each offering an
   * answer; it is consumed only when at least one answer line follows the
   * prompt, so a lone line after the link — the label over the organiser's
   * text — is never taken for a prompt.
   *
   * @param lines the text
   * @param from the first non-blank line after the event link line
   * @return the index past the block, or {@code from} itself when the line
   *         there does not begin one
   */
  private static int pastAnswerBlock(List<String> lines, int from) {
    int answers = 0;
    int i = from + 1;
    while (i < lines.size() && answers < ANSWERS_OFFERED && ANSWER_LINK_LINE.matcher(lines.get(i)).matches()) {
      answers++;
      i++;
    }
    return answers == 0 ? from : i;
  }

  /**
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
