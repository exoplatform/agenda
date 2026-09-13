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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.junit.Test;
import org.mockito.MockedStatic;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.resources.ResourceBundleService;

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

  /**
   * An answer label of 52 characters — past the 40 a label may be, which the
   * three shipped answer labels are nowhere near (30 at the longest, an
   * untranslated Crowdin placeholder). One translation of
   * {@code agenda.accepted} in this shape is all it would take.
   */
  private static final String LONG_ANSWER_LABEL = "Klikoni ketu per te pranuar kete ftese takimi xxxxx:";

  /** What the organiser typed, as the copy carries it. */
  private static final String DETAIL_AS_TEXT = "Bring cake.\nAnd plates.";

  /**
   * What the organiser typed, as the editor stores it — on one line, so the
   * builder's own rendering of a line break is not what these pins assert.
   */
  private static final String DETAIL    = "<p>Bring the <b>cake</b>.</p>";

  /** The layout's labels as the English bundle ships them, for the pins that render with a bundle. */
  private static final Map<String, String> ENGLISH_LABELS = englishLabels();

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
   * <b>A link under a sentence is not a link under a label.</b> The shapes
   * people actually write — a greeting, then a pasted event link, then a
   * signature; a sentence of context, then "Last meeting: <i>link</i>", then
   * more prose; a link line followed by a second link to something else — each
   * has the block's first two joints and not its last: the line after the link
   * is prose, and prose does not end in a colon. Every one of these five came
   * back with its head deleted before the narrowing this pin exists for — the
   * first three measured on this repository's review of EXO-90227, the last two
   * on the connector's.
   */
  @Test
  public void aLinkUnderASentenceRatherThanALabelIsReturnedUntouched() {
    String[] typed = { "Hi all,\nsee " + LINK_87 + "\nThanks,\nBob",
        "Follow-up of our last meeting.\n\nLast meeting: " + LINK_87
            + "\n\nPlease read the minutes before we start.\n\nAgenda: 1. budget 2. hiring",
        "Preparation:\n\nPrevious meeting: " + LINK_87 + "\n\nNotes: https://wiki.acme.com/x\n\nBring cake.",
        "Weekly sync.\nAgenda: " + LINK_87 + "\nNotes: bring the deck.\nRoom 4.",
        "Kickoff.\nSee: " + LINK_87 + "\nThat is all." };
    for (String text : typed) {
      assertSame("must be returned as given: " + text, text, InvitationText.stripFrom(text));
    }
  }

  /**
   * <b>A list of meetings is not a block.</b> Several event links under a
   * heading: the second link line is where the block's label would have to be,
   * and it is not one — so the whole list is kept, links, heading and the text
   * under it.
   */
  @Test
  public void aListOfEventLinksIsReturnedUntouched() {
    String text = "Related meetings:\nKickoff: " + LINK_87 + "\nReview: " + LINK_88 + "\nRetro: "
        + LINK_87.replace("eventId=87", "eventId=89") + "\nBring cake.";

    assertSame(text, InvitationText.stripFrom(text));
  }

  /**
   * <b>And the loop cannot eat its way down a text either.</b> Two
   * label-and-link shapes one after the other, each followed by a line of the
   * user's own: recognition stops at the first, so the six lines
   * {@code stripFrom} used to consume one pass at a time are all still there.
   */
  @Test
  public void repeatedLabelAndLinkShapesAreReturnedUntouched() {
    String text = "Notes:\nA: " + LINK_87 + "\nx\nNotes2:\nB: " + LINK_88 + "\ny\nBring cake.";

    assertSame(text, InvitationText.stripFrom(text));
  }

  /**
   * <b>A sentence that happens to end in an event link is not a label and a
   * link.</b> The label may be no longer than a label ever is; past that the
   * line is prose, whatever it ends with.
   */
  @Test
  public void aSentenceEndingInAnEventLinkIsNotTheBuildersLine() {
    String text = "Hello.\n\nThe minutes of the meeting we had in March are attached to " + LINK_87 + "\n\nEvent details:\nCake";

    assertSame(text, InvitationText.stripFrom(text));
  }

  /**
   * <b>An answer label longer than a label does not strand the answer links.</b>
   * The answer lines are read by the tokenised link they carry and by nothing
   * else, so no bound on a label can leave them below the point the block ends
   * at. Until EXO-90228 they were read under the same 40-character bound as the
   * event link's label, and this very input came back as <em>the answer link
   * itself</em>, presented as the organiser's text: the tokens re-emitted and
   * the prompt that said whose they were deleted. No shipped bundle reaches 40
   * today — the longest of the three answer labels across the 40
   * {@code Agenda_*} bundles is 30 — so this pin is about the Crowdin round
   * after next, which is where the blank-label guard came from too.
   */
  @Test
  public void anAnswerLabelLongerThanALabelStillLetsTheAnswerBlockBeConsumed() {
    String copy = "Invitation sent by alice2.\n\nEvent link: " + LINK_87 + "\n\nAnswer this invitation:\n" + LONG_ANSWER_LABEL
        + " " + ANSWER_87.formatted("ACCEPTED") + "\n\nEvent details:\nBring cake.";

    assertEquals("Bring cake.", InvitationText.stripFrom(copy));
  }

  /**
   * <b>Nor does a server rewriting the bracketed repetition of an answer
   * link.</b> BlueMind repeats every URI of a description in angle brackets;
   * the event link line requires that repetition to be the same link, and an
   * answer line — since EXO-90228 — requires nothing after the link at all. A
   * server that puts something else there therefore no longer ends the block
   * above the answers.
   */
  @Test
  public void aServerRewritingTheBracketedRepetitionOfAnAnswerLinkDoesNotStrandIt() {
    String copy = "Invitation sent by alice2.\n\nEvent link: " + LINK_87 + "\n\nAnswer this invitation:\nAccepted "
        + ANSWER_87.formatted("ACCEPTED") + " <" + LINK_88 + ">\n\nEvent details:\nBring cake.";

    assertEquals("Bring cake.", InvitationText.stripFrom(copy));
  }

  /**
   * <b>The invariant, asserted on the result rather than inferred from the
   * joints:</b> for any text at all, either it comes back as the very instance
   * given, or what comes back carries no answer link. A reading that takes a
   * block off and keeps somebody else's tokens under the organiser's name is
   * the one outcome worse than doing nothing — the tokens are re-emitted
   * <em>and</em> the attribution that said whose they were is gone — and
   * {@code AgendaEventRest} honours such a token as an identity with no session.
   *
   * <p>
   * The four inputs are the four ways found into that reading: an answer label
   * past the bound, a rewritten bracketed repetition, a blank line where the
   * builder wrote none, and more answer lines than the layout offers. The first
   * two are consumed correctly (the pins above); the last two are refused, and
   * that refusal is what this pin protects — remove the check in
   * {@code stripFrom} and they leak.
   */
  @Test
  public void noPartialStripEverKeepsSomebodyElsesAnswerToken() {
    String accepted = ANSWER_87.formatted("ACCEPTED");
    String head = "Invitation sent by alice2.\n\nEvent link: " + LINK_87 + "\n\nAnswer this invitation:";
    String[] texts = { head + "\n" + LONG_ANSWER_LABEL + " " + accepted + "\n\nEvent details:\nBring cake.",
        head + "\nAccepted " + accepted + " <" + LINK_88 + ">\n\nEvent details:\nBring cake.",
        head + "\n\nAccepted " + accepted + "\n\nEvent details:\nBring cake.",
        head + "\nAccepted " + accepted + "\nMaybe " + ANSWER_87.formatted("TENTATIVE") + "\nDeclined "
            + ANSWER_87.formatted("DECLINED") + "\nAlso " + accepted + "\n\nEvent details:\nBring cake." };
    for (String text : texts) {
      String stripped = InvitationText.stripFrom(text);
      if (stripped != text) {
        assertFalse("a reading that kept an answer token: " + stripped, stripped.contains("/response/send?"));
      }
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
   * <b>The same round trip, with the labels a reader actually sees.</b> The pin
   * above renders outside a portal container, where every label degrades to its
   * bundle key — so it fails on a drift in the layout and cannot see one that
   * needs a real label to show. This one reads the labels through the bundle
   * service, so the localised shape of the line the recogniser anchors on is
   * covered too, and asserts that it really did: a rendering that fell back to
   * the keys would prove nothing here.
   */
  @Test
  public void renderingWithLabelsFromABundleIsTheSameCopy() {
    ResourceBundleService bundles = bundleService(ENGLISH_LABELS);
    try (MockedStatic<ExoContainerContext> container = mockStatic(ExoContainerContext.class)) {
      container.when(() -> ExoContainerContext.getService(ResourceBundleService.class)).thenReturn(bundles);

      String rendered = EventIcsBuilder.description(Locale.ENGLISH,
                                                    "Alice Two",
                                                    "Chemistry",
                                                    "https://meet.example.test/room",
                                                    LINK_87,
                                                    answers(ANSWER_87),
                                                    DETAIL);

      assertTrue("the labels must come from the bundle, not from the keys: " + rendered,
                 rendered.startsWith("Invitation sent by Alice Two in space Chemistry.") && rendered.contains("\nEvent link: ")
                     && rendered.contains("\nEvent details:\n"));
      assertFalse("no key may survive in the render: " + rendered, rendered.contains("agenda."));
      assertEquals(rendered,
                   EventIcsBuilder.description(Locale.ENGLISH,
                                               "Alice Two",
                                               "Chemistry",
                                               "https://meet.example.test/room",
                                               LINK_87,
                                               answers(ANSWER_87),
                                               rendered));
    }
  }

  /**
   * <b>A locale whose bundle answers a label blank does not lose the layout.</b>
   * A key present with an empty value is what one Crowdin round can ship, and
   * it would otherwise write the event link with nothing in front of it — a
   * line the recogniser does not read as this builder's own, silently and for
   * that locale only. The label falls back to its key instead, so the line
   * keeps its shape and the round trip still holds.
   */
  @Test
  public void aBundleAnsweringALabelBlankStillWritesALineTheRecogniserReads() {
    Map<String, String> blankLink = new LinkedHashMap<>(ENGLISH_LABELS);
    blankLink.put("agenda.eventLink", "");
    blankLink.put("agenda.eventDetail", "");
    ResourceBundleService bundles = bundleService(blankLink);
    try (MockedStatic<ExoContainerContext> container = mockStatic(ExoContainerContext.class)) {
      container.when(() -> ExoContainerContext.getService(ResourceBundleService.class)).thenReturn(bundles);

      String rendered = EventIcsBuilder.description(Locale.ENGLISH, "Alice Two", null, null, LINK_87, null, DETAIL);

      assertTrue("the blank label must fall back to its key: " + rendered, rendered.contains("\nagenda.eventLink " + LINK_87));
      assertEquals(rendered,
                   EventIcsBuilder.description(Locale.ENGLISH, "Alice Two", null, null, LINK_87, null, rendered));
    }
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
   * The labels of the layout as {@code Agenda_en.properties} ships them —
   * copied from it, so a label reshaped there and not here shows up as a
   * failure of the pins that read this rather than as silence.
   *
   * @return the nine labels the description is composed of, keyed as the
   *         builder asks for them
   */
  private static Map<String, String> englishLabels() {
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put("agenda.invitationText", "Invitation sent by");
    labels.put("agenda.inSpace", "in space");
    labels.put("agenda.visioLink", "Video conference link:");
    labels.put("agenda.eventLink", "Event link:");
    labels.put("agenda.eventDetail", "Event details:");
    labels.put("agenda.rsvpPrompt", "Answer this invitation:");
    labels.put("agenda.accepted", "Accepted");
    labels.put("agenda.declined", "Declined");
    labels.put("agenda.tentative", "Maybe");
    return labels;
  }

  /**
   * A bundle service answering one set of labels, standing in for the portal
   * container the builder reads them through.
   *
   * @param labels the entries the bundle holds; a key absent from it is
   *          answered by the real {@code Utils} with the key itself, as in
   *          production
   * @return the service, which every locale asked of it answers the same way
   */
  private static ResourceBundleService bundleService(Map<String, String> labels) {
    ResourceBundle bundle = new ListResourceBundle() {
      @Override
      protected Object[][] getContents() {
        return labels.entrySet().stream().map(entry -> new Object[] { entry.getKey(), entry.getValue() }).toArray(Object[][]::new);
      }
    };
    ResourceBundleService service = mock(ResourceBundleService.class);
    when(service.getSharedResourceBundleNames()).thenReturn(new String[0]);
    when(service.getResourceBundle(any(String[].class), any(Locale.class))).thenReturn(bundle);
    return service;
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
