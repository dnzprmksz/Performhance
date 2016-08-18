package com.monitise.performhance.api;

import com.monitise.performhance.api.model.AddUserRequest;
import com.monitise.performhance.api.model.CriteriaResponse;
import com.monitise.performhance.api.model.CriteriaUserResponse;
import com.monitise.performhance.api.model.EmployeeScoreResponse;
import com.monitise.performhance.api.model.Response;
import com.monitise.performhance.api.model.ResponseCode;
import com.monitise.performhance.api.model.SimplifiedUser;
import com.monitise.performhance.entity.Criteria;
import com.monitise.performhance.entity.JobTitle;
import com.monitise.performhance.entity.Organization;
import com.monitise.performhance.entity.Review;
import com.monitise.performhance.entity.User;
import com.monitise.performhance.exceptions.BaseException;
import com.monitise.performhance.helpers.RelationshipHelper;
import com.monitise.performhance.helpers.SecurityHelper;
import com.monitise.performhance.services.CriteriaService;
import com.monitise.performhance.services.JobTitleService;
import com.monitise.performhance.services.OrganizationService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/users")
public class UserController {

    // region Dependencies

    @Autowired
    private SecurityHelper securityHelper;
    @Autowired
    private UserService userService;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private JobTitleService jobTitleService;
    @Autowired
    private RelationshipHelper relationshipHelper;
    @Autowired
    private CriteriaService criteriaService;

    // endregion

    @Secured({"ROLE_ADMIN", "ROLE_MANAGER"})
    @RequestMapping(value = "/", method = RequestMethod.POST)
    public Response<SimplifiedUser> addEmployee(@RequestBody AddUserRequest addUserRequest) throws BaseException {
        int organizationId = addUserRequest.getOrganizationId();
        securityHelper.checkAuthentication(organizationId);
        Organization organization = organizationService.get(organizationId);
        validateUserRequest(organization, addUserRequest);
        JobTitle jobTitle = jobTitleService.get(addUserRequest.getJobTitleId());

        User employee = new User(
                addUserRequest.getName(),
                addUserRequest.getSurname(),
                organization,
                jobTitle,
                addUserRequest.getUsername(),
                addUserRequest.getPassword()
        );
        User addedEmployee = userService.addEmployee(employee);

        SimplifiedUser responseEmployee = SimplifiedUser.fromUser(addedEmployee);
        Response<SimplifiedUser> response = new Response<>();
        response.setData(responseEmployee);
        response.setSuccess(true);
        return response;
    }

    @Secured({"ROLE_ADMIN", "ROLE_MANAGER"})
    @RequestMapping(value = "/{userId}", method = RequestMethod.GET)
    public Response<SimplifiedUser> getSingleUser(@PathVariable int userId) throws BaseException {
        User user = userService.get(userId);
        securityHelper.checkAuthentication(user.getOrganization().getId());

        SimplifiedUser responseUser = SimplifiedUser.fromUser(user);
        Response response = new Response();
        response.setData(responseUser);
        response.setSuccess(true);
        return response;
    }

    @Secured({"ROLE_ADMIN", "ROLE_MANAGER"})
    @RequestMapping(value = "/{userId}", method = RequestMethod.DELETE)
    public Response<Object> deleteUser(@PathVariable int userId) throws BaseException {
        User user = userService.get(userId);
        securityHelper.checkAuthentication(user.getOrganization().getId());
        userService.remove(userId);

        Response response = new Response<>();
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{userId}/criteria", method = RequestMethod.GET)
    public Response<List<CriteriaResponse>> getUserCriteriaList(@PathVariable int userId) throws BaseException {
        User user = userService.get(userId);
        securityHelper.checkAuthentication(user.getOrganization().getId());
        List<Criteria> criteriaList = user.getCriteriaList();

        List<CriteriaResponse> criteriaResponseList = CriteriaResponse.fromList(criteriaList);
        Response<List<CriteriaResponse>> response = new Response<>();
        response.setData(criteriaResponseList);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{userId}/criteria/{criteriaId}", method = RequestMethod.POST)
    public Response<CriteriaUserResponse> assignCriteriaToUser(@PathVariable int userId,
                                                               @PathVariable int criteriaId) throws BaseException {
        int organizationId = userService.get(userId).getOrganization().getId();
        securityHelper.checkAuthentication(organizationId);
        relationshipHelper.ensureOrganizationCriteriaRelationship(organizationId, criteriaId);
        User userFromService = criteriaService.assignCriteriaToUserById(criteriaId, userId);

        CriteriaUserResponse criteriaUserResponse = CriteriaUserResponse.fromUser(userFromService);
        Response<CriteriaUserResponse> response = new Response<>();
        response.setData(criteriaUserResponse);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/{userId}/criteria/{criteriaId}", method = RequestMethod.DELETE)
    public Response<Object> removeCriteriaFromUser(@PathVariable int userId,
                                                   @PathVariable int criteriaId) throws BaseException {
        int organizationId = userService.get(userId).getOrganization().getId();
        securityHelper.checkAuthentication(organizationId);
        relationshipHelper.ensureOrganizationCriteriaRelationship(organizationId, criteriaId);

        criteriaService.removeCriteriaFromUserById(criteriaId, userId);
        Response<Object> response = new Response<>();
        response.setSuccess(true);
        return response;
    }

    @RequestMapping(value = "/{userId}/score", method = RequestMethod.GET)
    public Response<EmployeeScoreResponse> getEmployeeReviewScore(@PathVariable int userId) throws BaseException {
        User employee = userService.get(userId);
        securityHelper.checkAuthentication(employee.getOrganization().getId());
        List<Review> employeeReviews = employee.getReviews();
        Map<String, Integer> criteriaScores = new HashMap<>();
        int reviewCount = employeeReviews.size();

        // The total score for each Criteria will be stored in an array.
        List<Criteria> employeeCriteriaList = employee.getCriteriaList();
        int employeeCriteriaCount = employeeCriteriaList.size();
        int[] criteriaTotalScore = new int[employeeCriteriaCount];

        for (Review review : employeeReviews) {
            Map<Criteria, Integer> evaluation = review.getEvaluation();
            // Add each criteria's score to corresponding array location.
            int criteriaIndex = 0;
            for (Map.Entry entry : evaluation.entrySet()) {
                int score = (int)entry.getValue();
                criteriaTotalScore[criteriaIndex] += score;
                criteriaIndex++;
            }
        }

        // Calculate average score for each criteria.
        int[] criteriaAverageScore = new int[employeeCriteriaCount];
        int index = 0;
        for (int totalScore : criteriaTotalScore) {
            criteriaAverageScore[index] = criteriaTotalScore[index] / reviewCount;
            index++;
        }

        index = 0;
        for (Criteria criteria : employeeCriteriaList) {
            String criteriaName = criteria.getCriteria();
            int criteriaAverageValue = criteriaAverageScore[index];
            criteriaScores.put(criteriaName, criteriaAverageValue);
            index++;
        }

        EmployeeScoreResponse employeeScoreResponse = new EmployeeScoreResponse(
                employee.getName(),
                reviewCount,
                criteriaScores
        );
        Response<EmployeeScoreResponse> response = new Response<>();
        response.setData(employeeScoreResponse);
        response.setSuccess(true);
        return response;
    }

    @Secured("ROLE_MANAGER")
    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public Response<List<SimplifiedUser>> searchUsers(
            @RequestParam(value = "titleId", required = false, defaultValue = UserService.UNDEFINED) String titleId,
            @RequestParam(value = "teamId", required = false, defaultValue = UserService.UNDEFINED) String teamId,
            @RequestParam(value = "name", required = false, defaultValue = UserService.UNDEFINED) String name,
            @RequestParam(value = "surname", required = false, defaultValue = UserService.UNDEFINED) String surname)
            throws BaseException {

        if (UserService.UNDEFINED.equals(titleId) && UserService.UNDEFINED.equals(teamId)
                && UserService.UNDEFINED.equals(name) && UserService.UNDEFINED.equals(surname)) {
            throw new BaseException(ResponseCode.SEARCH_MISSING_PARAMETERS,
                    "At least one of titleId, teamId, name or surname parameters must be specified.");
        }

        User manager = securityHelper.getAuthenticatedUser();
        Organization organization = manager.getOrganization();
        int organizationId = organization.getId();
        formatValidateSearchRequest(titleId, teamId);
        semanticallyValidate(organization, titleId, teamId);
        List<User> userList = userService.searchUsers(organizationId, teamId, titleId, name, surname);

        List<SimplifiedUser> simpleList = SimplifiedUser.fromUserList(userList);
        Response response = new Response();
        response.setData(simpleList);
        response.setSuccess(true);

        return response;
    }

    // region Helper Methods

    private void validateUserRequest(Organization organization, AddUserRequest employee) throws BaseException {
        String name = employee.getName();
        String surname = employee.getSurname();
        if (name == null || name.trim().equals("") || surname == null || surname.trim().equals("")) {
            throw new BaseException(ResponseCode.USER_USERNAME_NOT_EXIST, "Empty user name is not allowed.");
        }
        relationshipHelper.ensureOrganizationJobTitleRelationship(organization.getId(), employee.getJobTitleId());
    }

    private void formatValidateSearchRequest(String titleId, String teamId) throws BaseException {
        if ((!titleId.equals(UserService.UNDEFINED) && !isNonNegativeInteger(titleId))) {
            throw new BaseException(ResponseCode.SEARCH_INVALID_ID,
                    "titleId must be positive integers");
        }

        if ((!teamId.equals(UserService.UNDEFINED) && !isNonNegativeInteger(teamId))) {
            throw new BaseException(ResponseCode.SEARCH_INVALID_ID,
                    "teamId must be positive integers");
        }

    }

    private void semanticallyValidate(Organization organization, String titleId, String teamId) throws BaseException {
        int organizationId = organization.getId();

        // Check if the title is defined in the organization.
        if (!titleId.equals(UserService.UNDEFINED)) {
            int intTitleId = Integer.parseInt(titleId);
            if (!organizationService.isJobTitleDefined(organizationId, intTitleId)) {
                throw new BaseException(ResponseCode.JOB_TITLE_ID_DOES_NOT_EXIST,
                        "Given job title id is not existent in the organization");
            }
        }

        // Check if the team is defined in the organization.
        if (!teamId.equals(UserService.UNDEFINED)) {
            int intTeamId = Integer.parseInt(teamId);
            if (!organizationService.isTeamIdDefined(organizationId, intTeamId)) {
                throw new BaseException(ResponseCode.TEAM_ID_DOES_NOT_EXIST,
                        "Given team's id is not existent in the organization");
            }
        }
    }

    private boolean isNonNegativeInteger(String str) throws BaseException {
        int candidate;
        try {
            candidate = Integer.parseInt(str);
        } catch (NumberFormatException exception) {
            throw new BaseException(ResponseCode.SEARCH_INVALID_ID_FORMAT, "id's must be positive integers");
        }
        return candidate > 0;
    }

    // endregion

}
