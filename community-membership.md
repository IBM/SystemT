# Community membership

This document outlines the various responsibilities of contributor roles in SystemT.



| **Role**   | **Responsibilities**                                  | **Requirements**                                             | **Defined by**                                               |
| ---------- | ----------------------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| member     | active contributor in the community.  reviewer of PRs | sponsored by an approver or maintainer. multiple contributions to the project. | IBM GitHub org member.                             |
| triager     | assist with project management and backlog organization. | nominated by a maintainer. help with issues for one month. |  CONTRIBUTING, CODEOWNERS, or the README. |
| approver   | approve incoming contributions                        | highly experienced and active reviewer + contributor to a project | [CODEOWNERS](https://help.github.com/en/articles/about-code-owners) in GitHub |
| maintainer | set direction and priorities for a project         | demonstrated responsibility and excellent technical judgement for the project | [CODEOWNERS](https://help.github.com/en/articles/about-code-owners), GitHub Team and repo ownership in GitHub |
| emeritus   | position of honor for former maintainers, approvers, and triagers        | must have previously held a community role and not have been removed from that role for a [Code of Conduct](CODE_OF_CONDUCT.md) violation. | Listed as an emeritus maintainer/approver/triager in CONTRIBUTING, CODEOWNERS, or README |

## New contributors

New contributors should be welcomed to the community by existing members, helped
with PR workflow, and directed to relevant documentation and communication
channels.

## Established community members

Established community members are expected to demonstrate their adherence to the
principles in this document, familiarity with project organization, roles,
policies, procedures, conventions, etc., and technical and/or writing ability.
Role-specific expectations, responsibilities, and requirements are enumerated
below.

## Member

Members are continuously active contributors in the community. They can have
issues and PRs assigned to them. Members are expected to remain active contributors to the community.

Defined by: Member of the IBM GitHub organization

### Requirements

- Enabled [two-factor
  authentication](https://help.github.com/articles/about-two-factor-authentication)
  on their GitHub account
- Have made multiple contributions to the project or community. Contributions
  may include, but is not limited to:
  - Authoring or reviewing PRs on GitHub
  - Filing or commenting on issues on GitHub
  - Contributing to community discussions (e.g. meetings,
    chat, email, and discussion forums)
- Have read the [contributor
  guide](CONTRIBUTING.md)
- Actively contributing to 1 or more projects.
- Sponsored by an approver. Note the following requirements for sponsors:
  - Sponsors must have close interactions with the prospective member - e.g.
    code/design/proposal review, coordinating on issues, etc.
  - Sponsors must be maintainers.
- [Open an
  issue](https://github.com/IBM/SystemT/issues/new)
  against the
  [SystemT](https://github.com/IBM/SystemT) repo
  - Ensure your sponsors are `@mentioned` on the issue
  - Make sure that the list of contributions included is representative of your
    work on the project.
- Have your sponsoring reviewers reply confirmation of sponsorship: `I support`
- Once your sponsors have responded, your request will be reviewed by the
  Technical Committee (TC).  Any TC member can review the requirements and add
  Members to the GitHub org.

### Responsibilities and privileges

- Responsive to issues and PRs assigned to them
- Active owner of code they have contributed (unless ownership is explicitly
  transferred)
  - Code is well tested
  - Tests consistently pass
  - Addresses bugs or issues discovered after code is accepted
- Members can review and approve via the GitHub workflow.  This review work is
  not sufficient to merge a PR.  There will still need to be a review by an
  *maintainer*.
- Members can be assigned to issues and PRs, and people can ask members for
  reviews with a `/cc @username`.

Note: members who frequently contribute code are expected to proactively perform
code reviews and work towards becoming a *triager* for the project that
they are active in.  Acceptance of code contributions requires at least one
approver in addition to the reviews by *members.*

## Triager

Triagers assist the maintainers with project management and
backlog organization. The specific workflows and triage requirements depend on
the project, and are set by the project maintainers.

Defined by: [Triage permissions](https://help.github.com/en/github/setting-up-and-managing-organizations-and-teams/repository-permission-levels-for-an-organization#repository-access-for-each-permission-level),
with the names of the current Triagers commited to git, either in CONTRIBUTING,
CODEOWNERS, or the bottom of the README.

Triagers may be code contributors, but writing code is not a requirement for
becoming a triager. Triagers are encouraged to be active participants in project
meetings, chat rooms, and other discussion forums.

### Requirements

- Nominated by a maintainer, with no objections from other maintainers.
- Consistently interact with the community and help with issues for at least 1 month.

### Responsibilities and privileges

- Have an understanding of the goals and workflows defined by the maintainers.
- Respond to new PRs and Issues by asking clarifying questions.
- Organize the backlog by applying labels, milestones, assignees, and projects.

## Approver

Code approvers are able to both review and approve code contributions, as well
as help maintainers triage issues and do project management.

While code review is focused on code quality and correctness, approval is
focused on holistic acceptance of a contribution including: backwards/forwards
compatibility, adhering to API and flag conventions, subtle performance and
correctness issues, interactions with other parts of the system, etc.

Defined by: [CODEOWNERS
workflow](https://help.github.com/en/articles/about-code-owners).

Approver status can be scoped to a part of the codebase. For example, critical
core components may have higher bar for becoming an approver.

### Requirements

The following apply to the part of the codebase for which one would be an
approver in the `CODEOWNERS` files.

- Reviewer of the codebase for at least 1 month
- Reviewer for or author of at least 10 substantial PRs to the codebase,
  with the definition of substantial subject to the maintainer's discretion
  (e.g. refactors/adds new functionality rather than one-line pulls).
- Nominated by a maintainer
  - With no objections from other maintainers
  - Done through PR to update the `CODEOWNERS`.

### Responsibilities and privileges

The following apply to the part of the codebase for which one would be an
approver in the `CODEOWNERS` files.

- Approver status may be a precondition to accepting large code contributions
- Demonstrate sound technical judgement (may be asked to step down by a maintainer if they lose confidence of the maintainers)
- Responsible for project quality control via code reviews
  - Focus on holistic acceptance of contribution such as dependencies with other
    features, backwards / forwards compatibility, API and flag definitions, etc
- Expected to be responsive to review requests (inactivity for more than 1 month may result in suspension until active again)
- Mentor contributors and reviewers
- May approve code contributions for acceptance

## Maintainer

Note: This is a generalized high-level description of the role, and the
specifics of the maintainer role's responsibilities and related processes maybe 
defined for individual projects.

Maintainers are the technical authority for the SystemT project.  They 
*MUST* have demonstrated both good judgement and responsibility
towards the health of that project. Maintainers *MUST* set technical
direction and make or approve design decisions for their project - either
directly or through delegation of these responsibilities.

Defined by: GitHub organization ownership, permissions and entry in `CODEOWNERS`
files.

### Requirements

Unlike the roles outlined above, the maintainers of a project are typically
limited to a relatively small group of decision makers and updated as fits
the needs of the project.

The following apply to the project for which one would be a maintainer.

- Deep understanding of the technical goals and direction of the project
- Deep understanding of the technical domain (specifically the language) of the
  project
- Sustained contributions to design and direction by doing all of:
  - Authoring and reviewing proposals
  - Initiating, contributing and resolving discussions (emails, GitHub issues,
    meetings)
  - Identifying subtle or complex issues in designs and implementation PRs
- Directly contributed to the project through implementation and / or review
- Aligning with the overall project goals, specifications and design principles
  defined by Technical Committee (TC). Bringing general questions and requests
  to the discussions as part of specifications project.

### Responsibilities and privileges

The following apply to the project for which one would be a maintainer.

- Make and approve technical design decisions for the project.
- Set technical direction and priorities for the project.
- Define milestones and releases.
  - Decides on when PRs are merged to control the release scope.
- Mentor and guide approvers, reviewers, and contributors to the project.
- Escalate *reviewer* and *maintainer* workflow concerns (i.e. responsiveness,
  availability, and general contributor community health) to the TC.
- Ensure continued health of project:
  - Adequate test coverage to confidently release
  - Tests are passing reliably (i.e. not flaky) and are fixed when they fail
- Ensure a healthy process for discussion and decision making is in place.
- Work with other maintainers to maintain the project's overall health and
  success holistically.

### Becoming a Maintainer

Unless stated otherwise in a project charter ratified by the Technical Committee,
a new maintainer is elected by vote of the existing maintainers of the project.
The vote is officially started when a pull request to add a new maintainer
is opened, and ends when the pull request is merged. The pull request may be
merged when the following conditions are met:

- The person being nominated has accepted the nomination by approving the pull request
- All maintainers have approved the pull request OR a majority of maintainers
  have approved the pull request and no maintainer has objected by requesting
  changes on the pull request. In the case that all maintainers have not given
  approval, the pull request should stay open for a minimum of 5 days before merging.

The nominee is considered a maintainer after the pull request is merged.

#### Self-nomination is encouraged

If you feel like you meet the requirements above and are willing to take on the
additional responsibilities and privileges of being a maintainer, it is
recommended that you approach an existing maintainer about sponsoring your bid
to become a maintainer. After you and your sponsor have discussed the role
and its additional requirements and responsibilities, they may approach the other
maintainers about a vote to confirm you as a new maintainer. If the maintainer
does not believe you are ready for the role, or the project is not in need
of additional maintainers, they may suggest an alternate role or growth areas
in order to improve your chances to become a maintainer in the future.

## Emeritus Maintainer/Approver/Triager

For any healthy open source project, it is inevitable that contributors may move
on, step down, or otherwise reduce their role in the project for some period of
time. These people may have expertise in one or more area of the project, but no
longer have time to actively contribute or approve contributions. They are
encouraged to reflect this by adding themselves to the `emeritus` section of the
CONTRIBUTING, CODEOWNERS, or README of the project they are stepping away
from. GitHub usernames listed under the `emeritus` section should be removed
from the approvers list, and will no longer have any official responsibilities
with the project. When a contributor returns to being more active, they may be
promoted back to a their previous position at the discretion of the current
maintainers following the process defined above.