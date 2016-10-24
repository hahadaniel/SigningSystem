package com.xuemiao.service;

import com.xuemiao.api.Json.SignInActionJson;
import com.xuemiao.api.Json.SignInInfoCoursesInfo;
import com.xuemiao.api.Json.SignInInfoJson;
import com.xuemiao.api.Json.SignInInfoTimeSegment;
import com.xuemiao.exception.StudentNotExistException;
import com.xuemiao.model.pdm.*;
import com.xuemiao.model.pdm.primaryKey.CoursePerWeekPKey;
import com.xuemiao.model.pdm.primaryKey.FingerprintPK;
import com.xuemiao.model.repository.*;
import com.xuemiao.utils.DateUtils;
import com.xuemiao.utils.FingerprintUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by root on 16-10-19.
 */
@Component
public class SignInInfoService {
    @Autowired
    FingerprintRepository fingerprintRepository;
    @Autowired
    SignInInfoV2Repository signInInfoV2Repository;
    @Autowired
    SignInInfoRecordRepository signInInfoRecordRepository;
    @Autowired
    AbsencesService absencesService;
    @Value("${course.start_date}")
    String courseStartDateString;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    CoursePerWeekRepository coursePerWeekRepository;

    private Long checkFingerPrint(String fingerprint) {
        FingerprintPK fingerprintPK = new FingerprintPK();
        return fingerprintRepository.findByToken(fingerprint).getStudentId();
    }

    public void signIn(SignInActionJson signInActionJson) throws StudentNotExistException{
        DateTime dateTimeNow = DateTime.now();

        Long studentId = this.checkFingerPrint(signInActionJson.getFingerprint());
        if(studentId==null){
            throw new StudentNotExistException();
        }
        SignInInfoV2Entity signInInfoV2Entity = signInInfoV2Repository.findOneByStudentIdAndDate(studentId, new Date(dateTimeNow.getMillis()));
        Timestamp now = new Timestamp(dateTimeNow.getMillis());
        SignInInfoRecordEntity signInInfoRecordEntity = signInInfoRecordRepository.findOneUnfinishedSignInRecord(signInInfoV2Entity.getId());
        if (signInInfoRecordEntity == null) {
            signInInfoRecordEntity = new SignInInfoRecordEntity();
            signInInfoRecordEntity.setSignInInfoId(signInInfoV2Entity.getId());
            signInInfoRecordEntity.setStartTime(now);
        } else {
            signInInfoRecordEntity.setEndTime(now);
        }
        signInInfoRecordRepository.save(signInInfoRecordEntity);
    }

    public Date getSignInInfoLatestDate() {
        return signInInfoV2Repository.getLatestSignInInfoDate();
    }

    public List<SignInInfoJson> getSignInInfoJsonsByDate(Date date) {
        List<SignInInfoV2Entity> signInInfoV2Entities = signInInfoV2Repository.findByOperDate(new Date(DateTime.now().getMillis()));
        List<SignInInfoJson> signInInfoJsonList = new ArrayList<>();
        for (SignInInfoV2Entity signInInfoV2Entity : signInInfoV2Entities) {
            signInInfoJsonList.add(wrapSignInInfoIntoJson(signInInfoV2Entity, date));
        }
        return signInInfoJsonList;
    }

    public void addStudentIntoSignInInfo(StudentEntity studentEntity) {
        SignInInfoV2Entity signInInfoV2Entity = new SignInInfoV2Entity();
        signInInfoV2Entity.setStudentId(studentEntity.getStudentId());
        signInInfoV2Entity.setOperDate(new Date(DateTime.now().getMillis()));
        signInInfoV2Repository.save(signInInfoV2Entity);
    }

    public void deleteSignInInfoByStudentId(Long id) {
        List<SignInInfoV2Entity> signInInfoV2Entities = signInInfoV2Repository.findByStudentId(id);
        for (SignInInfoV2Entity signInInfoV2Entity : signInInfoV2Entities) {
            signInInfoRecordRepository.deleteBySignInInfoId(signInInfoV2Entity.getId());
        }
        signInInfoV2Repository.deleteByStudentId(id);
    }

    private String getTimeSegmentWidth(Timestamp start,Timestamp end){
        return 100*(end.getTime()-start.getTime())/(18*3600*1000) + "%";
    }

    private String getTimeSegmentWidth(String start,String end){
        DateTimeFormatter format = DateTimeFormat.forPattern("HH:mm");
        DateTime startDateTime = DateTime.parse(start,format);
        DateTime endDateTime = DateTime.parse(end,format);
        return this.getTimeSegmentWidth(new Timestamp(startDateTime.getMillis()),new Timestamp(endDateTime.getMillis()));
    }

    private SignInInfoJson wrapSignInInfoIntoJson(SignInInfoV2Entity signInInfoV2Entity, Date date) {
        DateTime dateTime = new DateTime(date);
        DateTime startDate = DateTime.parse(courseStartDateString);
        int currentWeek = DateUtils.getCurrentWeek(startDate, dateTime);
        int currentWeekday = DateUtils.getCurrentWeekDay(startDate, dateTime);

        DateTime signInDate = new DateTime(signInInfoV2Entity.getOperDate());
        DateTime now = DateTime.now();

        SignInInfoJson signInInfoJson = new SignInInfoJson();
        signInInfoJson.setStudentId(signInInfoV2Entity.getStudentId().toString());
        signInInfoJson.setName(studentRepository.findOne(signInInfoV2Entity.getStudentId()).getName());
        List<SignInInfoTimeSegment> signInInfoTimeSegments = new ArrayList<>();
        List<SignInInfoRecordEntity> signInInfoRecordEntities = signInInfoRecordRepository.findBySignInInfoId(signInInfoV2Entity.getId());
        for (SignInInfoRecordEntity signInInfoRecordEntity : signInInfoRecordEntities){
            SignInInfoTimeSegment signInInfoTimeSegment = new SignInInfoTimeSegment();
            signInInfoTimeSegment.setStartTime(DateUtils.timestamp2String(signInInfoRecordEntity.getStartTime(),3));
            if(signInInfoRecordEntity.getEndTime()!=null){
                signInInfoTimeSegment.setEndTime(DateUtils.timestamp2String(signInInfoRecordEntity.getEndTime(),3));
                signInInfoTimeSegment.setType(1);
                signInInfoTimeSegment.setWidth(getTimeSegmentWidth(signInInfoRecordEntity.getStartTime(),
                        signInInfoRecordEntity.getEndTime()));
            }
            else if (signInDate.getYear()==now.getYear()&&signInDate.getMonthOfYear()==now.getMonthOfYear()&&signInDate.getDayOfMonth()==now.getDayOfMonth()){
                signInInfoTimeSegment.setType(0);
                signInInfoTimeSegment.setWidth(getTimeSegmentWidth(signInInfoRecordEntity.getStartTime(),new Timestamp(now.getMillis())));
            }

            signInInfoTimeSegments.add(signInInfoTimeSegment);
        }

        List<CourseEntity> courseEntities = courseRepository.getCoursesByStudentAndWeek(signInInfoV2Entity.getStudentId(), currentWeek);
        List<SignInInfoCoursesInfo> signInInfoCoursesInfoList = new ArrayList<>();
        for (CourseEntity courseEntity : courseEntities) {
            SignInInfoTimeSegment signInInfoTimeSegment = wrapCourseIntoSignInCourseInfoJson(courseEntity, currentWeekday);
            if(signInInfoTimeSegment!=null){
                signInInfoTimeSegments.add(signInInfoTimeSegment);
            }
        }

        AbsenceEntity absenceEntity = absencesService.getAbsenceByIdAndDate(signInInfoV2Entity.getStudentId(), signInInfoV2Entity.getOperDate());
        if (absenceEntity != null) {
            SignInInfoTimeSegment signInInfoTimeSegment = new SignInInfoTimeSegment();
            signInInfoTimeSegment.setStartTime(DateUtils.timestamp2String(absenceEntity.getStartAbsence(),3));
            signInInfoTimeSegment.setEndTime(DateUtils.timestamp2String(absenceEntity.getEndAbsence(),3));
            signInInfoTimeSegment.setExtra(absenceEntity.getAbsenceReason());
            signInInfoTimeSegment.setType(3);
            signInInfoTimeSegment.setWidth(getTimeSegmentWidth(absenceEntity.getStartAbsence(),absenceEntity.getEndAbsence()));
            signInInfoTimeSegments.add(signInInfoTimeSegment);
        }

        Collections.sort(signInInfoTimeSegments);

        if(signInInfoTimeSegments.size()==0){
            SignInInfoTimeSegment signInInfoTimeSegment = new SignInInfoTimeSegment();
            signInInfoTimeSegment.setStartTime("06:00");
            signInInfoTimeSegment.setType(4);
            if(signInDate.getYear()==now.getYear()&&signInDate.getMonthOfYear()==now.getMonthOfYear()&&signInDate.getDayOfMonth()==now.getDayOfMonth()){
                signInInfoTimeSegment.setEndTime(String.format("%02d", now.getHourOfDay())+":"+String.format("%02d", now.getMinuteOfHour()));
            }
            else {
                signInInfoTimeSegment.setEndTime("24:00");
            }
            signInInfoTimeSegment.setWidth(getTimeSegmentWidth(signInInfoTimeSegment.getStartTime(),signInInfoTimeSegment.getEndTime()));
            signInInfoTimeSegment.setExtra("not in lab1");
            signInInfoTimeSegments.add(signInInfoTimeSegment);
        }
        else{
            List<SignInInfoTimeSegment> signInInfoTimeSegmentsTemp = new ArrayList<>();

            SignInInfoTimeSegment signInInfoTimeSegment = new SignInInfoTimeSegment();
            signInInfoTimeSegment.setStartTime("06:00");
            signInInfoTimeSegment.setEndTime(signInInfoTimeSegments.get(0).getStartTime());
            signInInfoTimeSegment.setType(4);
            signInInfoTimeSegment.setWidth(getTimeSegmentWidth(signInInfoTimeSegment.getStartTime(),signInInfoTimeSegment.getEndTime()));
            signInInfoTimeSegment.setExtra("not in lab2");
            signInInfoTimeSegmentsTemp.add(signInInfoTimeSegment);

            for(int i=1;i<signInInfoTimeSegments.size();i++){
                signInInfoTimeSegment = new SignInInfoTimeSegment();
                signInInfoTimeSegment.setStartTime(signInInfoTimeSegments.get(i-1).getEndTime());
                signInInfoTimeSegment.setEndTime(signInInfoTimeSegments.get(i).getStartTime());
                signInInfoTimeSegment.setType(4);
                signInInfoTimeSegment.setWidth(getTimeSegmentWidth(signInInfoTimeSegment.getStartTime(),signInInfoTimeSegment.getEndTime()));
                signInInfoTimeSegment.setExtra("not in lab3");
                signInInfoTimeSegmentsTemp.add(signInInfoTimeSegment);
            }

            SignInInfoTimeSegment tailTimeSegment = signInInfoTimeSegments.get(signInInfoTimeSegments.size()-1);
            if(tailTimeSegment.getEndTime()!=null){
                signInInfoTimeSegment = new SignInInfoTimeSegment();
                signInInfoTimeSegment.setStartTime(tailTimeSegment.getEndTime());
                signInInfoTimeSegment.setType(4);
                if(signInDate.getYear()==now.getYear()&&signInDate.getMonthOfYear()==now.getMonthOfYear()&&signInDate.getDayOfMonth()==now.getDayOfMonth()){
                    signInInfoTimeSegment.setEndTime(String.format("%02d", now.getHourOfDay())+":"+String.format("%02d", now.getMinuteOfHour()));
                }
                else {
                    signInInfoTimeSegment.setEndTime("24:00");
                }
                signInInfoTimeSegment.setWidth(getTimeSegmentWidth(signInInfoTimeSegment.getStartTime(),signInInfoTimeSegment.getEndTime()));
                signInInfoTimeSegment.setExtra("not in lab4");
                signInInfoTimeSegmentsTemp.add(signInInfoTimeSegment);
            }
            signInInfoTimeSegments.addAll(signInInfoTimeSegmentsTemp);
        }

        Collections.sort(signInInfoTimeSegments);

        signInInfoJson.setSignInInfoTimeSegments(signInInfoTimeSegments);
        return signInInfoJson;
    }

    private SignInInfoTimeSegment wrapCourseIntoSignInCourseInfoJson(CourseEntity courseEntity, int currentWeekday) {
        CoursePerWeekPKey coursePerWeekPKey = new CoursePerWeekPKey();
        coursePerWeekPKey.setCourseId(courseEntity.getId());
        coursePerWeekPKey.setWeekday(currentWeekday);
        CoursePerWeekEntity coursePerWeekEntity = coursePerWeekRepository.findOne(coursePerWeekPKey);
        SignInInfoTimeSegment signInInfoTimeSegment = new SignInInfoTimeSegment();
        if (coursePerWeekEntity != null) {
            signInInfoTimeSegment.setStartTime(getCourseTime(coursePerWeekEntity.getStartSection(),1));
            signInInfoTimeSegment.setEndTime(getCourseTime(coursePerWeekEntity.getEndSection(),2));
            signInInfoTimeSegment.setType(2);
            signInInfoTimeSegment.setExtra(courseEntity.getCourseName());
            signInInfoTimeSegment.setWidth(getTimeSegmentWidth(signInInfoTimeSegment.getStartTime(),
                    signInInfoTimeSegment.getEndTime()));
            return signInInfoTimeSegment;
        }
        return null;
    }

    private String getCourseTime(int section,int type){
        String courseTime = null;
        if(type==1){
            switch (section){
                case 1:
                    courseTime = "8:50";
                    break;
                case 2:
                    courseTime = "9:40";
                    break;
                case 3:
                    courseTime = "10:40";
                    break;
                case 4:
                    courseTime = "11:30";
                    break;
                case 5:
                    courseTime = "14:00";
                    break;
                case 6:
                    courseTime = "14:50";
                    break;
                case 7:
                    courseTime = "15:40";
                    break;
                case 8:
                    courseTime = "16:30";
                    break;
                case 9:
                    courseTime = "19:00";
                    break;
                case 10:
                    courseTime = "19:50";
                    break;
                case 11:
                    courseTime = "20:40";
                    break;
                default:
                    courseTime = "";
            }
            return courseTime;
        }
        else if(type==2){
            switch (section){
                case 1:
                    courseTime = "9:35";
                    break;
                case 2:
                    courseTime = "10:25";
                    break;
                case 3:
                    courseTime = "11:25";
                    break;
                case 4:
                    courseTime = "12:15";
                    break;
                case 5:
                    courseTime = "14:45";
                    break;
                case 6:
                    courseTime = "15:35";
                    break;
                case 7:
                    courseTime = "16:25";
                    break;
                case 8:
                    courseTime = "17:15";
                    break;
                case 9:
                    courseTime = "19:45";
                    break;
                case 10:
                    courseTime = "20:35";
                    break;
                case 11:
                    courseTime = "21:25";
                    break;
                default:
                    courseTime = "";
            }
        }
        return courseTime;
    }

}