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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

/**
 * A description that already carries the builder's invitation text is given
 * one block, the current one — never a second (EXO-90227) — and text a person
 * typed is never mistaken for that block.
 */
public class InvitationTextTest {

  /** The address the rig's copies carried, as Stalwart returned it. */
  private static final String LINK_87   = "http://localhost:8080/portal/dw/agenda?eventId=87";

  /** The other user's event on the same deployment. */
  private static final String LINK_88   = "http://localhost:8080/portal/dw/agenda?eventId=88";

  /** An answer link minted for one event and one person. */
  private static final String ANSWER_87 = "http://localhost:8080/portal/rest/v1/agenda/events/87/response/send?response=%s&token=alice2&redirect=true";

  /** And for the other. */
  private static final String ANSWER_88 = "http://localhost:8080/portal/rest/v1/agenda/events/88/response/send?response=%s&token=alice&redirect=true";

  /** What the organiser typed, as the copy carries it. */
  private static final String DETAIL_AS_TEXT = "Bring cake.\nAnd plates.";

  /**
   * What the organiser typed, as the editor stores it — on one line, so the
   * builder's own rendering of a line break is not what these pins assert.
   */
  private static final String DETAIL    = "<p>Bring the <b>cake</b>.</p>";

  // ---------------------------------------------------------------- reading

  /**
   * <b>The field shape.</b> The copy alice2's push wrote, read back by alice:
   * French labels, alice2's name, alice2's event. What is left is what the
   * organiser typed — not the attribution, not the link that alice's next
   * push would otherwise wrap in a block of its own.
   */
  @Test
  public void anotherUsersBlockIsTakenOffAndTheOrganisersTextIsKept() {
    String copy = "Invitation envoyée par alice2.\n\nLien de l'événement : " + LINK_87
        + "\n\nDétails de l'événement :\n" + DETAIL_AS_TEXT;

    assertEquals(DETAIL_AS_TEXT, InvitationText.stripFrom(copy));
  }

  /**
   * What the rig held after three edit round trips — one block per edit,
   * alternating between the two users' event ids — comes back to the
   * organiser's text in one reading rather than one layer per reading.
   */
  @Test
  public void aDescriptionThatAlreadyStackedThreeBlocksIsBroughtBackToTheTextInOneGo() {
    String once = "Invitation sent by alice2.\n\nEvent link: " + LINK_87 + "\n\nEvent details:\n" + DETAIL_AS_TEXT;
    String twice = "Invitation sent by alice.\n\nEvent link: " + LINK_88 + "\n\nEvent details:\n" + once;
    String thrice = "Invitation sent by alice2.\n\nEvent link: " + LINK_87 + "\n\nEvent details:\n" + twice;

    assertEquals(DETAIL_AS_TEXT, InvitationText.stripFrom(thrice));
  }

  /**
   * A meeting with nothing typed into it: the block was all there was, with
   * or without answer links under it, and nothing remains.
   */
  @Test
  public void aBlockWithNoTextUnderItLeavesNothing() {
    assertEquals("", InvitationText.stripFrom("Invitation sent by alice2.\n\nEvent link: " + LINK_87));
    assertEquals("",
                 InvitationText.stripFrom("Invitation sent by alice2.\n\nEvent link: " + LINK_87
                     + "\n\nAnswer this invitation:\nAccepted " + ANSWER_87.formatted("ACCEPTED")));
  }

  /**
   * As BlueMind returns a copy: the blank lines folded into a newline and a
   * continuation space, every link repeated in angle brackets. Still the
   * builder's text, still taken off.
   */
  @Test
  public void aBlockAServerFoldedAndLinkifiedIsStillRecognised() {
    String copy = "Invitation sent by Root Root in space Chemistry.\n Video conference link: https://meet.example.test/r <https://meet.example.test/r>\n Event link: "
        + LINK_87 + " <" + LINK_87 + ">\n Answer this invitation:\nAccepted " + ANSWER_87.formatted("ACCEPTED") + " <"
        + ANSWER_87.formatted("ACCEPTED") + ">\nMaybe " + ANSWER_87.formatted("TENTATIVE") + "\nDeclined "
        + ANSWER_87.formatted("DECLINED") + "\n Event details:\nBring cake.";

    assertEquals("Bring cake.", InvitationText.stripFrom(copy));
  }

  /**
   * A copy read back without a scheme on its link (the EXO-89824 shape), and
   * one written by a deployment serving from a path: both are the builder's.
   */
  @Test
  public void aLinkWithoutASchemeOrUnderAPathIsStillTheBuildersLine() {
    assertEquals("Cake",
                 InvitationText.stripFrom("Invitation sent by Root Root. \n\nEvent link: ai-contribution-ft.meeds.io/portal/dw/agenda?eventId=1\n\nEvent details:\nCake"));
    assertEquals("Cake",
                 InvitationText.stripFrom("Invitation sent by Root Root.\n\nEvent link: https://exo.example.test/intranet/portal/dw/agenda?eventId=42\n\nEvent details:\nCake"));
  }

  /**
   * <b>Text a person typed is kept whole</b>, even when it carries a link to
   * an eXo event: pasted into a sentence, on the first line, in the fourth
   * paragraph, with words after it, bare on a line, repeated in brackets as a
   * different link, or to another page of the portal. None begins like the
   * block, and the very text given is returned.
   */
  @Test
  public void aPersonsOwnTextCarryingAnEventLinkIsReturnedUntouched() {
    String[] typed = { "See " + LINK_87 + " for the agenda.\n\nBring cake.",
        "Event link: " + LINK_87 + "\n\nBring cake.",
        "Hello.\n\nFirst.\n\nSecond.\n\nLink: " + LINK_87 + "\n\nBring cake.",
        "Hello.\n\nLink: " + LINK_87 + " (old one)\n\nBring cake.",
        "Hello.\n\n" + LINK_87 + "\n\nBring cake.",
        "Hello.\n\nSee: " + LINK_87 + " <" + LINK_88 + ">\n\nBring cake.",
        "Hello.\n\nWiki: https://exo.example.test/portal/dw/wiki?eventId=3\n\nBring cake.",
        "Just a plain description." };
    for (String text : typed) {
      assertSame("must be returned as given: " + text, text, InvitationText.stripFrom(text));
    }
  }

  /**
   * Nothing to read is nothing to change.
   */
  @Test
  public void nothingIsNothing() {
    assertNull(InvitationText.stripFrom(null));
    assertEquals("", InvitationText.stripFrom(""));
    assertEquals("  ", InvitationText.stripFrom("  "));
  }

  // ---------------------------------------------------------------- writing

  /**
   * <b>The defect, at the builder.</b> Given, as the event's description, the
   * text another user's push had composed — their name, their event, their
   * answer links — the builder writes its own block and the organiser's text,
   * byte for byte what it writes from the organiser's text alone. Nothing of
   * the other user's block survives: not their event id, and above all not
   * their tokens, which answer as them.
   */
  @Test
  public void aDescriptionCarryingAnotherUsersBlockIsRenderedWithThisRendersBlockAlone() {
    String theirs = EventIcsBuilder.description(Locale.FRENCH, "alice2", null, null, LINK_87, answers(ANSWER_87), DETAIL);
    assertTrue("the fixture must be a real render naming the other event: " + theirs, theirs.contains(LINK_87));

    String fresh = EventIcsBuilder.description(Locale.ENGLISH, "alice", "Chemistry", null, LINK_88, answers(ANSWER_88), DETAIL);
    String rerendered = EventIcsBuilder.description(Locale.ENGLISH, "alice", "Chemistry", null, LINK_88, answers(ANSWER_88), theirs);

    assertEquals(fresh, rerendered);
    assertFalse("the other user's event must not be named: " + rerendered, rerendered.contains("eventId=87"));
    assertFalse("the other user's tokens must not be re-issued: " + rerendered, rerendered.contains("token=alice2"));
    assertTrue("the organiser's text must survive: " + rerendered, rerendered.contains("Bring the cake."));
  }

  /**
   * <b>The single-user round trip stays stable</b> — the regression guard.
   * Rendering what the builder itself rendered gives the very same text, so
   * a copy read back and pushed again is the same copy, and the mirror sees
   * nothing to rewrite.
   */
  @Test
  public void renderingWhatTheBuilderRenderedIsTheSameCopy() {
    String rendered = EventIcsBuilder.description(Locale.ENGLISH,
                                                  "Alice Two",
                                                  "Chemistry",
                                                  "https://meet.example.test/room",
                                                  LINK_87,
                                                  answers(ANSWER_87),
                                                  DETAIL);

    assertEquals(rendered,
                 EventIcsBuilder.description(Locale.ENGLISH,
                                             "Alice Two",
                                             "Chemistry",
                                             "https://meet.example.test/room",
                                             LINK_87,
                                             answers(ANSWER_87),
                                             rendered));
  }

  /**
   * The HTML flavour — X-ALT-DESC of the mailed and downloaded document —
   * writes one block as well, and the organiser's text under it.
   */
  @Test
  public void theHtmlFlavourWritesOneBlockToo() {
    String theirs = EventIcsBuilder.description(Locale.FRENCH, "alice2", null, null, LINK_87, null, DETAIL);

    String html = EventIcsBuilder.htmlDescription(Locale.ENGLISH, "alice", "Chemistry", null, LINK_88, theirs);

    assertFalse("the other user's block must not be nested: " + html, html.contains("alice2"));
    assertFalse("nor their event linked: " + html, html.contains("eventId=87"));
    assertTrue("this render's link must be there: " + html, html.contains(LINK_88));
    assertTrue("the organiser's text must survive: " + html, html.contains("Bring the cake."));
  }

  /**
   * When the block is taken off, what remains is rendered from text: escaped,
   * with each line break as a {@code <br>} — not as the character reference
   * the encoder writes for a newline, which a mail client renders as nothing.
   */
  @Test
  public void theHtmlFlavourRendersTheRemainingLinesAsHtml() {
    String copy = "Invitation envoyée par alice2.\n\nLien de l'événement : " + LINK_87
        + "\n\nDétails de l'événement :\nBring cake.\nAnd plates & forks.";

    String html = EventIcsBuilder.htmlDescription(Locale.ENGLISH, "alice", "Chemistry", null, LINK_88, copy);

    assertTrue("lines as <br>, text escaped: " + html, html.contains("Bring cake.<br>And plates &amp; forks."));
    assertFalse("no character reference for a newline: " + html, html.contains("&#xa;"));
  }

  /**
   * <b>Notifications do not change for ordinary events.</b> A description that
   * carries no block reaches the HTML flavour exactly as the editor stored it,
   * markup and all — the document attached to a mail is what it always was.
   */
  @Test
  public void anOrdinaryDescriptionKeepsItsMarkupInTheHtmlFlavour() {
    String html = EventIcsBuilder.htmlDescription(Locale.ENGLISH,
                                                  "Ada Lovelace",
                                                  "Chemistry",
                                                  null,
                                                  LINK_88,
                                                  "<p>Bring the <b>slides</b>.</p>");

    assertTrue("the markup must pass through untouched: " + html, html.contains("<p>Bring the <b>slides</b>.</p>"));
  }

  /**
   * The three answer links for one event, as the connector hands them in.
   *
   * @param template the link with the answer left to fill in
   * @return the links keyed by answer
   */
  private static Map<EventAttendeeResponse, String> answers(String template) {
    Map<EventAttendeeResponse, String> links = new LinkedHashMap<>();
    links.put(EventAttendeeResponse.ACCEPTED, template.formatted("ACCEPTED"));
    links.put(EventAttendeeResponse.TENTATIVE, template.formatted("TENTATIVE"));
    links.put(EventAttendeeResponse.DECLINED, template.formatted("DECLINED"));
    return links;
  }
}
