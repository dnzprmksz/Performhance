package com.monitise.performhance.api;

import com.monitise.performhance.api.model.AddJobTitleRequest;
import com.monitise.performhance.api.model.AddTeamRequest;
import com.monitise.performhance.api.model.ExtendedResponse;
import com.monitise.performhance.api.model.Response;
import com.monitise.performhance.api.model.ResponseCode;
import com.monitise.performhance.api.model.SimplifiedTeam;
import com.monitise.performhance.api.model.SimplifiedUser;
import com.monitise.performhance.api.model.TeamResponse;
import com.monitise.performhance.api.model.UpdateTeamRequest;
import com.monitise.performhance.entity.Organization;
import com.monitise.performhance.entity.Team;
import com.monitise.performhance.exceptions.BaseException;
import com.monitise.performhance.helpers.RelationshipHelper;
import com.monitise.performhance.helpers.SecurityHelper;
import com.monitise.performhance.helpers.Util;
import com.monitise.performhance.services.CriteriaService;
import com.monitise.performhance.services.OrganizationService;
import com.monitise.performhance.services.TeamService;
import com.monitise.performhance.services.UserService;
import org.omg.CORBA.Object;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static com.monitise.performhance.helpers.Util.generateExistingUsersMessage;

@RestController
@RequestMapping("/teams")
public class TeamController {

    // region Dependencies

    @Autowired
    private TeamService teamService;
    @Autowired
    private SecurityHelper securityHelper;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private UserService userService;
    @Autowired
    private RelationshipHelper relationshipHelper;
    @Autowired
    private CriteriaService criteriaService;

    // endregion

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public Response<List<SimplifiedTeam>> getAll() throws BaseException {
        int organizationId = securityHelper.getAuthenticatedUser().getOrganization().getId();
        List<Team> list = teamService.getListFilterByOrganizationId(organizationId);

        List<SimplifiedTeam> responseList = SimplifiedTeam.fromList(list);
        Response<List<SimplifiedTeam>> response = new Response<>();
        response.setData(responseList);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{teamId}", method = RequestMethod.GET)
    public Response<TeamResponse> get(@PathVariable int teamId) throws BaseException {
        checkAuthentication(teamId);
        Team team = teamService.get(teamId);

        TeamResponse responseTeam = TeamResponse.fromTeam(team);
        Response<TeamResponse> response = new Response<>();
        response.setData(responseTeam);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/", method = RequestMethod.POST)
    public Response<TeamResponse> add(@RequestBody AddTeamRequest addTeamRequest) throws BaseException {
        int organizationId = securityHelper.getAuthenticatedUser().getOrganization().getId();
        Organization organization = organizationService.get(organizationId);
        validateAddTeamRequest(addTeamRequest);
        Team team = new Team(addTeamRequest.getName(), organization);
        Team addedTeam = teamService.add(team);

        TeamResponse responseTeam = TeamResponse.fromTeam(addedTeam);
        Response<TeamResponse> response = new Response<>();
        response.setData(responseTeam);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{teamId}", method = RequestMethod.PUT)
    public Response<TeamResponse> update(@PathVariable int teamId,
                                         @RequestBody UpdateTeamRequest updateTeamRequest) throws BaseException {
        checkAuthentication(teamId);
        validateUpdateTeamRequest(updateTeamRequest);
        Team teamFromService = teamService.updateFromRequest(teamId, updateTeamRequest);

        TeamResponse teamResponse = TeamResponse.fromTeam(teamFromService);
        Response<TeamResponse> response = new Response<>();
        response.setData(teamResponse);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{teamId}", method = RequestMethod.DELETE)
    public Response<Object> remove(@PathVariable int teamId) throws BaseException {
        checkAuthentication(teamId);
        teamService.remove(teamId);
        Response<Object> response = new Response<>();
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{teamId}/leader", method = RequestMethod.DELETE)
    public Response<TeamResponse> removeTeamLeader(@PathVariable int teamId) throws BaseException {
        checkAuthentication(teamId);
        Team updatedTeam = teamService.removeLeadershipFromTeam(teamId);

        TeamResponse teamResponse = TeamResponse.fromTeam(updatedTeam);
        Response<TeamResponse> response = new Response<>();
        response.setData(teamResponse);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{teamId}/leader/{userId}", method = RequestMethod.POST)
    public Response<TeamResponse> assignTeamLeader(@PathVariable int teamId,
                                                   @PathVariable int userId) throws BaseException {
        checkAuthentication(teamId);
        securityHelper.checkAuthentication(userService.get(userId).getOrganization().getId());
        Team updatedTeam = teamService.assignLeaderToTeam(userId, teamId);

        TeamResponse teamResponse = TeamResponse.fromTeam(updatedTeam);
        Response<TeamResponse> response = new Response<>();
        response.setData(teamResponse);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{teamId}/users/{userId}", method = RequestMethod.POST)
    public Response<TeamResponse> assignEmployee(@PathVariable int teamId,
                                                 @PathVariable int userId) throws BaseException {
        checkAuthentication(teamId);
        securityHelper.checkAuthentication(userService.get(userId).getOrganization().getId());
        Team updatedTeam = teamService.assignEmployeeToTeam(userId, teamId);

        TeamResponse teamResponse = TeamResponse.fromTeam(updatedTeam);
        Response<TeamResponse> response = new Response<>();
        response.setData(teamResponse);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{teamId}/users/{userId}", method = RequestMethod.DELETE)
    public Response<TeamResponse> removeEmployeeFromTeam(@PathVariable int teamId,
                                                         @PathVariable int userId) throws BaseException {
        checkAuthentication(teamId);
        securityHelper.checkAuthentication(userService.get(userId).getOrganization().getId());
        Team updatedTeam = teamService.removeEmployeeFromTeam(userId, teamId);

        TeamResponse teamResponse = TeamResponse.fromTeam(updatedTeam);
        Response<TeamResponse> response = new Response<>();
        response.setData(teamResponse);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{teamId}/criteria/{criteriaId}", method = RequestMethod.POST)
    public Response<Object> assignCriteriaToTeamUsers(@PathVariable int teamId,
                                                      @PathVariable int criteriaId) throws BaseException {
        checkAuthentication(teamId);
        securityHelper.checkAuthentication(criteriaService.get(criteriaId).getOrganization().getId());
        ArrayList<Integer> existingUserList = criteriaService.assignCriteriaToTeam(criteriaId, teamId);

        ExtendedResponse<Object> response = new ExtendedResponse<>();
        response.setMessage(generateExistingUsersMessage(existingUserList));
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public Response<List<SimplifiedUser>> searchUsers(
            @RequestParam(value = "teamName", required = false, defaultValue = TeamService.UNDEFINED) String teamName)
            throws BaseException {
        if (teamName.equals(UserService.UNDEFINED)) {
            throw new BaseException(ResponseCode.SEARCH_MISSING_PARAMETERS, "teamName parameter must be specified.");
        }
        int organizationId = securityHelper.getAuthenticatedUser().getOrganization().getId();
        List<Team> teamList = teamService.searchTeams(organizationId, teamName);

        List<TeamResponse> teamResponseList = TeamResponse.fromTeamList(teamList);
        Response response = new Response();
        response.setData(teamResponseList);
        response.setSuccess(true);
        return response;
    }

    // region Helper Methods

    private void validateUpdateTeamRequest(UpdateTeamRequest updateRequest) throws BaseException {
        String name = updateRequest.getName();
        if (Util.isNullOrEmpty(name)) {
            throw new BaseException(ResponseCode.INVALID_TEAM_NAME, "Team name can not be empty");
        }
    }

    private void validateAddTeamRequest(AddTeamRequest request) throws BaseException {
        String name = request.getName();
        if (Util.isNullOrEmpty(name)) {
            throw new BaseException(ResponseCode.INVALID_TEAM_NAME, "Team name can not be empty");
        }
    }

    private void checkAuthentication(int teamId) throws BaseException {
        Team team = teamService.get(teamId);
        Organization organization = team.getOrganization();
        int organizationId = organization.getId();
        securityHelper.checkAuthentication(organizationId);
    }

    // endregion

}