package com.monitise.performhance.services;

import com.monitise.performhance.api.model.ResponseCode;
import com.monitise.performhance.api.model.UpdateJobTitleRequest;
import com.monitise.performhance.entity.JobTitle;
import com.monitise.performhance.entity.Organization;
import com.monitise.performhance.exceptions.BaseException;
import com.monitise.performhance.repositories.JobTitleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobTitleService {

    @Autowired
    private JobTitleRepository jobTitleRepository;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private UserService userService;

    public JobTitle add(JobTitle jobTitle) throws BaseException {
        Organization organization = jobTitle.getOrganization();
        ensureUniquenessInOrganization(jobTitle, organization.getId());
        JobTitle jobTitleFromRepo = jobTitleRepository.save(jobTitle);
        if (jobTitleFromRepo == null) {
            throw new BaseException(ResponseCode.UNEXPECTED, "Could not add given job title.");
        }
        addJobTitleToOrganization(jobTitle, organization);
        return jobTitleFromRepo;
    }

    public List<JobTitle> getAll() {
        return jobTitleRepository.findAll();
    }

    public JobTitle get(int id) throws BaseException {
        JobTitle jobTitle = jobTitleRepository.findOne(id);
        if (jobTitle == null) {
            throw new BaseException(ResponseCode.JOB_TITLE_ID_DOES_NOT_EXIST,
                    "A job title with given ID does not exist.");
        }
        return jobTitle;
    }

    public List<JobTitle> getListFilterByOrganizationId(int organizationId) throws BaseException {
        return organizationService.get(organizationId).getJobTitles();
    }

    public JobTitle update(JobTitle jobTitle) throws BaseException {
        ensureExistence(jobTitle.getId());
        JobTitle jobTitleFromRepo = jobTitleRepository.save(jobTitle);
        if (jobTitleFromRepo == null) {
            throw new BaseException(ResponseCode.UNEXPECTED, "Could not update given Job Title.");
        }
        return jobTitleFromRepo;
    }

    public JobTitle updateFromRequest(UpdateJobTitleRequest updateJobTitleRequest, int jobTitleId) throws BaseException {
        JobTitle jobTitle = get(jobTitleId);
        ensureNameIsUnique(updateJobTitleRequest, jobTitle.getOrganization().getId());
        jobTitle.setTitle(updateJobTitleRequest.getTitle());
        return update(jobTitle);
    }


    public void remove(int jobTitleId) throws BaseException {
        ensureExistence(jobTitleId);
        ensureJobTitleIsNotUsed(jobTitleId);
        removeJobTitleFromOrganization(jobTitleId);
        jobTitleRepository.delete(jobTitleId);
    }

    // region Helper Methods

    private void ensureNameIsUnique(UpdateJobTitleRequest request, int organizationId) throws BaseException {
        JobTitle jobTitle = jobTitleRepository.findByTitleAndOrganizationId(request.getTitle(), organizationId);
        if (jobTitle != null) {
            throw new BaseException(ResponseCode.JOB_TITLE_EXISTS_IN_ORGANIZATION,
                    "You organization already has job title: " + request.getTitle() + ".");
        }
    }

    private void ensureUniquenessInOrganization(JobTitle jobTitle, int organizationId) throws BaseException {
        JobTitle jobTitleFromRepo = jobTitleRepository.findByTitleAndOrganizationId(jobTitle.getTitle(),
                organizationId);
        if (jobTitleFromRepo != null) {
            throw new BaseException(ResponseCode.JOB_TITLE_EXISTS_IN_ORGANIZATION,
                    "Given job title already exists in this organization.");
        }
    }

    private void ensureExistence(int jobTitleId) throws BaseException {
        JobTitle jobTitle = jobTitleRepository.findOne(jobTitleId);
        if (jobTitle == null) {
            throw new BaseException(ResponseCode.JOB_TITLE_ID_DOES_NOT_EXIST,
                    "A job title with given ID does not exist.");
        }
    }

    private void ensureJobTitleIsNotUsed(int jobTitleId) throws BaseException {
        int employeesWithThisJobTitle = userService.getIdListByJobTitleId(jobTitleId).size();
        if (employeesWithThisJobTitle > 0) {
            throw new BaseException(ResponseCode.JOB_TITLE_IN_USE,
                    "Cannot remove the Job Title when there are employees using this Job Title.");
        }
    }

    private void removeJobTitleFromOrganization(int jobTitleId) throws BaseException {
        JobTitle jobTitle = get(jobTitleId);
        Organization organization = jobTitle.getOrganization();
        organization.getJobTitles().remove(jobTitle);
        organizationService.update(organization);
    }

    private void addJobTitleToOrganization(JobTitle jobTitle, Organization organization) throws BaseException {
        organization.getJobTitles().add(jobTitle);
        organizationService.update(organization);
    }

    // endregion

}