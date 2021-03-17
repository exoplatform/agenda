export function computeVoters(event) {
  const dateOptions = event && event.dateOptions || [];
  const attendees = event && event.attendees || [];
  if (dateOptions) {
    dateOptions.sort((dateOption1, dateOption2) => dateOption1.start.localeCompare(dateOption2.start));
  }
  if (attendees.length) {
    return computeVotersFromAttendees(attendees, dateOptions);
  } else {
    return Promise.resolve([]);
  }
}

function computeVotersFromAttendees(attendees, dateOptions) {
  const voters = [];
  const currentUserId = Number(eXo.env.portal.userIdentityId);
  attendees.forEach(attendee => {
    computeVoterFromIdentity(currentUserId, dateOptions, voters, attendee);
  });
  voters.sort((voter1, voter2) => 
    Number(voter1.id) === currentUserId && -1
    || Number(voter2.id) === currentUserId && 1
    || (voter1.space && !voter2.space && 1)
    || (voter2.space && !voter1.space && -1)
    || (voter1.hasVoted && !voter2.hasVoted && -1)
    || (voter2.hasVoted && !voter1.hasVoted && 1)
    || voter1.fullName.localeCompare(voter2.fullName)
  );
  return voters;
}

function computeVoterFromIdentity(currentUserId, dateOptions, voters, attendee) {
  const voter = attendee.identity;
  voter.fullName = voter.profile && voter.profile.fullname || voter.space && voter.space.displayName || '';
  if (Number(voter.id) === currentUserId) {
    voter.isCurrentUser = true;
  }
  voter.hasVoted = attendee.response === 'TENTATIVE';
  voter.dateOptionVotes = [];
  dateOptions.forEach(dateOption => {
    const acceptedVote = dateOption && dateOption.voters && dateOption.voters.indexOf(Number(voter.id)) >= 0 || false;
    voter.dateOptionVotes.push(acceptedVote);
  });
  voters.push(voter);
}
