package com.meiji.toutiao.module.wenda.article;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.meiji.toutiao.ErrorAction;
import com.meiji.toutiao.RetrofitFactory;
import com.meiji.toutiao.api.IMobileWendaApi;
import com.meiji.toutiao.bean.wenda.WendaArticleBean;
import com.meiji.toutiao.bean.wenda.WendaArticleDataBean;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Meiji on 2017/5/20.
 */

class WendaArticlePresenter implements IWendaArticle.Presenter {

    private static final String TAG = "WendaArticlePresenter";
    private IWendaArticle.View view;
    private int time;
    private Gson gson = new Gson();
    private List<WendaArticleDataBean> dataList = new ArrayList<>();


    WendaArticlePresenter(IWendaArticle.View view) {
        this.view = view;
        this.time = (int) (new Date(System.currentTimeMillis()).getTime() / 1000);
    }

    @Override
    public void doLoadData() {

        // 释放内存
        if (dataList.size() > 100) {
            dataList.clear();
        }

        RetrofitFactory.getRetrofit().create(IMobileWendaApi.class)
                .getWendaArticle(time)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .flatMap(new Function<WendaArticleBean, Observable<WendaArticleDataBean>>() {
                    @Override
                    public Observable<WendaArticleDataBean> apply(@NonNull WendaArticleBean wendaArticleBean) throws Exception {

                        List<WendaArticleDataBean> list = new ArrayList<>();
                        for (WendaArticleBean.DataBean bean : wendaArticleBean.getData()) {
                            WendaArticleDataBean contentBean = gson.fromJson(bean.getContent(), WendaArticleDataBean.class);
                            list.add(contentBean);
                        }
                        return Observable.fromIterable(list);
                    }
                })
                .filter(new Predicate<WendaArticleDataBean>() {
                    @Override
                    public boolean test(@NonNull WendaArticleDataBean wendaArticleDataBean) throws Exception {
                        return !TextUtils.isEmpty(wendaArticleDataBean.getQuestion());
                    }
                })
                .map(new Function<WendaArticleDataBean, WendaArticleDataBean>() {
                    @Override
                    public WendaArticleDataBean apply(@NonNull WendaArticleDataBean bean) throws Exception {

                        WendaArticleDataBean.ExtraBean extraBean = gson.fromJson(bean.getExtra(), WendaArticleDataBean.ExtraBean.class);
                        WendaArticleDataBean.QuestionBean questionBean = gson.fromJson(bean.getQuestion(), WendaArticleDataBean.QuestionBean.class);
                        WendaArticleDataBean.AnswerBean answerBean = gson.fromJson(bean.getAnswer(), WendaArticleDataBean.AnswerBean.class);
                        bean.setExtraBean(extraBean);
                        bean.setQuestionBean(questionBean);
                        bean.setAnswerBean(answerBean);

                        time = bean.getBehot_time();
                        return bean;
                    }
                })
                .filter(new Predicate<WendaArticleDataBean>() {
                    @Override
                    public boolean test(@NonNull WendaArticleDataBean wendaArticleDataBean) throws Exception {
                        for (WendaArticleDataBean bean : dataList) {
                            if (bean.getQuestionBean().getTitle().equals(wendaArticleDataBean.getQuestionBean().getTitle())) {
                                return false;
                            }
                        }
                        return true;
                    }
                })
                .toList()
                .compose(view.<List<WendaArticleDataBean>>bindToLife())
                .subscribe(new Consumer<List<WendaArticleDataBean>>() {
                    @Override
                    public void accept(@NonNull List<WendaArticleDataBean> wendaArticleDataBeen) throws Exception {
                        doSetAdapter(wendaArticleDataBeen);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        doShowNetError();
                        ErrorAction.print(throwable);
                    }
                });
    }

    @Override
    public void doLoadMoreData() {
        doLoadData();
    }

    @Override
    public void doRefresh() {
        if (dataList.size() != 0) {
            dataList.clear();
            time = (int) (new Date(System.currentTimeMillis()).getTime() / 1000);
        }
        doLoadData();
    }

    @Override
    public void doShowNetError() {
        view.onHideLoading();
        view.onShowNetError();
    }

    @Override
    public void doSetAdapter(List<WendaArticleDataBean> list) {
        dataList.addAll(list);
        view.onSetAdapter(dataList);
        view.onHideLoading();
    }
}
